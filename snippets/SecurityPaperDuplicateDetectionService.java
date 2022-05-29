package com.wealthpilot.asset.securitypaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.springframework.data.util.Pair;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.math.DoubleMath;
import com.wealthpilot.asset.ApiType;
import com.wealthpilot.asset.HistoryRun;
import com.wealthpilot.asset.HistoryType;
import com.wealthpilot.asset.account.ApiAccount;
import com.wealthpilot.b2b.service.B2BApiSource;
import com.wealthpilot.util.LoggingUtil;
import com.wealthpilot.util.ParallelListProcessingService;
import com.wealthpilot.util.async.ForkJoinPoolFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class SecurityPaperDuplicateDetectionService {

    private final ApiSecurityPaperHistoryService apiSecurityPaperHistoryService;
    private final ApiSecurityPaperHistoryWriteService apiSecurityPaperHistoryWriteService;
    private final ApiSecurityPaperHistoryRepository apiSecurityPaperHistoryRepository;
    private final ApiSecurityPaperRepository apiSecurityPaperRepository;
    private final ApiSecurityPaperRetrievalService apiSecurityPaperRetrievalService;
    private final ApiSecurityPaperTransactionRepository apiSecurityPaperTransactionRepository;
    private final ExpenseOrIncomeRepository expenseOrIncomeRepository;
    private final ForkJoinPool pool = ForkJoinPoolFactory.createForkJoinPool(10, "duplicate-merger-", Thread.MIN_PRIORITY);
    private final ParallelListProcessingService parallelListProcessingService;
    private final EntityManager entityManager;

    public void findAndMergeDuplicates() {
        log.info("Starting to find and merge duplicates");
        String queryString = """
                           SELECT apiAccount.id
                           FROM ApiAccount apiAccount
                           WHERE apiAccount.accountTypeId = com.wealthpilot.asset.account.ApiAccount.ACCOUNT_TYPE_SECURITIES
                        """;
        final List<Long> depotIds = entityManager.createQuery(queryString, Long.class).getResultList(); // around 342k depots
        log.info("Retrieved depot ids: {}", depotIds.size());
        try {
            parallelListProcessingService.processInBatches(pool, depotIds, 1000, this::findAndMergeDuplicatesInDepots);
            log.info("End of merging duplicate security papers");
        } catch (Exception e) {
            log.info("Error merging duplicate security papers: ", e);
        }
    }

    protected Stream<Boolean> findAndMergeDuplicatesInDepots(final List<Long> depotIds) {
        Stream.Builder<Boolean> builder = Stream.builder();
        try {
            log.info("Starting to find duplicates batch of {} ids", depotIds.size());
            List<ApiAccount> apiAccounts = getApiAccounts(depotIds);
            int count = 0;
            for (ApiAccount apiAccount : apiAccounts) {
                count++;
                boolean recordModified = detectAndMergeDuplicates(apiAccount);
                builder.add(recordModified);
                if (LoggingUtil.shouldLogProgress(count, depotIds.size(), 10)) {
                    log.info("Finding duplicates in block of depots: {}/{}", count, depotIds.size());
                }
            }
        } catch (Exception e) {
            log.error("Error occurred during detection of duplicate batch account ids: {}", depotIds, e);
        }
        log.info("Finished batch");
        return builder.build();
    }

    private List<ApiAccount> getApiAccounts(final List<Long> apiAccountIds) {
        String queryString = """
                           SELECT apiAccount
                           FROM ApiAccount apiAccount
                           WHERE apiAccount.id in (:ids)
                        """;

        TypedQuery<ApiAccount> query = entityManager.createQuery(queryString, ApiAccount.class);
        query.setParameter("ids", apiAccountIds);
        return query.getResultList();
    }

    private boolean detectAndMergeDuplicates(ApiAccount apiAccount) {
        boolean duplicateDetected = false;
        Multimap<String, ApiSecurityPaper> paperMap = ArrayListMultimap.create();
        List<ApiSecurityPaper> securityPapers = apiSecurityPaperRetrievalService.findByApiAccount(apiAccount);
        for (final ApiSecurityPaper securityPaper : securityPapers) {
            String isin = securityPaper.getIsin();
            Collection<ApiSecurityPaper> duplicates = paperMap.get(isin);
            if (!duplicates.isEmpty()) {
                duplicateDetected = true;
            }
            paperMap.put(isin, securityPaper);
        }

        mergeDetectedDuplicateSecurityPapers(paperMap);
        return duplicateDetected;
    }

    private void mergeDetectedDuplicateSecurityPapers(@NonNull final Multimap<String, ApiSecurityPaper> paperMap) {
        for (String isin : paperMap.keySet()) {
            Collection<ApiSecurityPaper> duplicates = paperMap.get(isin);
            if (duplicates.size() > 1) {
                log.info("Try to merge multiple security papers with same isin {}", isin);
                List<Pair<ApiSecurityPaper, Optional<LocalDate>>> duplicatesWithDate = getFirstHistoryDate(duplicates);
                while (duplicatesWithDate.size() > 1) {
                    mergeDuplicatesIntoTargetPaper(duplicatesWithDate);
                }
            }
        }
    }

    @NonNull
    private List<Pair<ApiSecurityPaper, Optional<LocalDate>>> getFirstHistoryDate(@NonNull final Collection<ApiSecurityPaper> papers) {
        return new ArrayList<>(papers.stream()
                                     .map(paper -> Pair.of(paper, apiSecurityPaperHistoryRepository.findFirstRecordedDateByApiSecurityPaper(paper)))
                                     .toList());
    }

    private void mergeDuplicatesIntoTargetPaper(@NonNull List<Pair<ApiSecurityPaper, Optional<LocalDate>>> duplicatesWithDate) {
        Pair<ApiSecurityPaper, Optional<LocalDate>> paperWithDate = duplicatesWithDate.remove(0);
        ApiSecurityPaper paperToMerge = paperWithDate.getFirst();
        Optional<LocalDate> firstHistoryDate = paperWithDate.getSecond();
        if (firstHistoryDate.isEmpty()) {
            log.info("No history for security-paper - will not merge: {} with {}", paperToMerge, duplicatesWithDate);
            return;
        }

        boolean paperWasMerged = false;
        List<Pair<ApiSecurityPaper, Optional<LocalDate>>> entriesToRemove = new ArrayList<>();
        for (Pair<ApiSecurityPaper, Optional<LocalDate>> otherPaperWithDate : duplicatesWithDate) {
            ApiSecurityPaper otherPaperToMerge = otherPaperWithDate.getFirst();
            Optional<LocalDate> otherPaperFirstHistoryDate = otherPaperWithDate.getSecond();
            if (otherPaperFirstHistoryDate.isEmpty()) {
                log.info("No history for security-paper - will not merge: {} with {}", otherPaperToMerge, paperToMerge);
                entriesToRemove.add(otherPaperWithDate);
            } else if (canMergeHistory(paperToMerge, otherPaperToMerge)) {
                if (firstHistoryDate.get().isBefore(otherPaperFirstHistoryDate.get())) {
                    paperWasMerged = mergeDuplicates(otherPaperToMerge, paperToMerge);
                    // otherPaperToMerge has been eliminated, so we can remove it from the list
                    entriesToRemove.add(otherPaperWithDate);
                } else {
                    paperWasMerged = mergeDuplicates(paperToMerge, otherPaperToMerge);
                    // paperToMerge has been eliminated, so we can stop processing it
                    break;
                }
            } else {
                log.info("Duplicate detected with conflicting history: {}, {}", paperToMerge, otherPaperToMerge);
            }
        }
        if (!paperWasMerged) {
            log.info("Paper could not be merged with any duplicates: {}, {}", paperToMerge, duplicatesWithDate);
        }
        duplicatesWithDate.removeAll(entriesToRemove);
    }

    private boolean mergeDuplicates(ApiSecurityPaper securityPaperToMerge, ApiSecurityPaper targetSecurityPaper) {
        try {
            log.info("Start merging duplicate security papers: target={} - securityPaperToMerge={}", targetSecurityPaper, securityPaperToMerge);
            migrateHistory(securityPaperToMerge, targetSecurityPaper);
            deleteTransactionsBasedCalculatedOrManualOrAutofillHistory(targetSecurityPaper);
            migrateExpenseOrIncomes(securityPaperToMerge, targetSecurityPaper);
            migrateTransactions(securityPaperToMerge, targetSecurityPaper);
            migrateSaleDate(securityPaperToMerge, targetSecurityPaper);
            apiSecurityPaperRepository.save(targetSecurityPaper);
            apiSecurityPaperRepository.delete(securityPaperToMerge);
            apiSecurityPaperRepository.flush();
            apiSecurityPaperHistoryWriteService.fillHistoryAndCalculateRateOfReturnData(targetSecurityPaper);
            log.info("End merging duplicate security papers: target={} - securityPaperToMerge={}", targetSecurityPaper, securityPaperToMerge);
            return true;
        } catch (Exception e) {
            log.error("Failed to merge security-paper {} into {}:", securityPaperToMerge, targetSecurityPaper, e);
            return false;
        }
    }

    private void migrateSaleDate(final ApiSecurityPaper securityPaperToMerge, final ApiSecurityPaper targetSecurityPaper) {
        LocalDate targetSecurityPaperSaleDate = targetSecurityPaper.getSaleDate();
        LocalDate sourceSaleDate = securityPaperToMerge.getSaleDate();
        if (targetSecurityPaperSaleDate != null //
                        && (sourceSaleDate != null && sourceSaleDate.isAfter(targetSecurityPaperSaleDate)) || (sourceSaleDate == null)) {
            targetSecurityPaper.setSaleDate(sourceSaleDate);
            log.info("Setting sale date of target security paper from {} to {} {}", targetSecurityPaperSaleDate, sourceSaleDate, targetSecurityPaper);
        } else {
            log.info("Leaving sale date of target security paper unchanged: {} {}", targetSecurityPaperSaleDate, targetSecurityPaper);
        }
    }

    private boolean canMergeHistory(ApiSecurityPaper securityPaper, ApiSecurityPaper duplicate) {
        Map<LocalDate, ApiSecurityPaperHistory> paperHistories = getRecordedHistoryByDate(securityPaper);
        Map<LocalDate, ApiSecurityPaperHistory> duplicateHistories = getRecordedHistoryByDate(duplicate);
        if (paperHistories.size() < 5 && duplicateHistories.size() < 5) {
            log.warn("Very little history to compare: {}, {}", paperHistories, duplicateHistories);
        }

        AtomicInteger countHistoryChanges = new AtomicInteger();
        AtomicInteger countQuantityChanges = new AtomicInteger();
        List<Pair<ApiSecurityPaperHistory, ApiSecurityPaperHistory>> conflicts =
                        analyzeHistoryConflicts(securityPaper, duplicate, paperHistories, duplicateHistories, countHistoryChanges, countQuantityChanges);
        if (conflicts.isEmpty()) {
            log.info("History can be merged without conflicts: {} history-changes, {} quantity-changes for {}, {}", countHistoryChanges, countQuantityChanges,
                            securityPaper, duplicate);
            return true;
        }
        if (conflicts.size() < 2 && conflicts.stream().allMatch(this::hasMatchingQuantity)) {
            log.info("History can be merged in spite of conflicts: {}, {} history-changes, {} quantity-changes for {}, {}", conflicts, countHistoryChanges,
                            countQuantityChanges, securityPaper, duplicate);
            return true;
        }
        LocalDate firstConflictDate = conflicts.get(0).getFirst().getHistoryDate();
        log.info("History conflicts cannot be merged: {} conflicts, first on {}. Not merging security-papers {}, {}", conflicts.size(), firstConflictDate,
                        securityPaper, duplicate);
        return false;
    }

    @NonNull
    private List<Pair<ApiSecurityPaperHistory, ApiSecurityPaperHistory>> analyzeHistoryConflicts(final ApiSecurityPaper securityPaper,
                    final ApiSecurityPaper duplicate, final Map<LocalDate, ApiSecurityPaperHistory> paperHistories,
                    final Map<LocalDate, ApiSecurityPaperHistory> duplicateHistories, final AtomicInteger countHistoryChanges,
                    final AtomicInteger countQuantityChanges) {
        LocalDate firstHistoryDate =
                        Streams.concat(paperHistories.keySet().stream(), duplicateHistories.keySet().stream()).min(Comparator.naturalOrder()).orElseThrow();

        List<Pair<ApiSecurityPaperHistory, ApiSecurityPaperHistory>> conflicts = new ArrayList<>();
        ApiSecurityPaper lastHistorySource = null;
        Double lastKnownQuantity = null;
        for (LocalDate historyDate = firstHistoryDate; historyDate.isBefore(LocalDate.now()); historyDate = historyDate.plusDays(1)) {
            ApiSecurityPaperHistory history = paperHistories.get(historyDate);
            ApiSecurityPaperHistory otherHistory = duplicateHistories.get(historyDate);
            if (history != null) {
                incrementCounters(lastHistorySource, lastKnownQuantity, securityPaper, history, countHistoryChanges, countQuantityChanges);
                lastHistorySource = securityPaper;
                lastKnownQuantity = history.getQuantityNominal();
            } else if (otherHistory != null) {
                incrementCounters(lastHistorySource, lastKnownQuantity, duplicate, otherHistory, countHistoryChanges, countQuantityChanges);
                lastHistorySource = duplicate;
                lastKnownQuantity = otherHistory.getQuantityNominal();
            }
            if (history != null && otherHistory != null) {
                conflicts.add(Pair.of(history, otherHistory));
            }
        }
        return conflicts;
    }

    private boolean hasMatchingQuantity(@NonNull final Pair<ApiSecurityPaperHistory, ApiSecurityPaperHistory> conflict) {
        return hasMatchingQuantity(conflict.getFirst(), conflict.getSecond());
    }

    private boolean hasMatchingQuantity(@NonNull final ApiSecurityPaperBase securityPaper, @NonNull final ApiSecurityPaperBase duplicate) {
        Double quantityNominal = securityPaper.getQuantityNominal();
        Double quantityNominalDup = duplicate.getQuantityNominal();
        return quantityNominal != null && quantityNominalDup != null && DoubleMath.fuzzyEquals(quantityNominal, quantityNominalDup, 0.001d);
    }

    private void incrementCounters(final ApiSecurityPaper lastHistorySource, final Double lastKnownQuantity, final ApiSecurityPaper securityPaper,
                    final ApiSecurityPaperHistory history, final AtomicInteger countHistoryChanges, final AtomicInteger countQuantityChanges) {
        if (lastHistorySource != null && lastHistorySource != securityPaper) {
            countHistoryChanges.incrementAndGet(); // history changed
            if (!Objects.equals(lastKnownQuantity, history.getQuantityNominal())) {
                countQuantityChanges.incrementAndGet(); // history and quantity changed
            }
        }
    }

    @NonNull
    private Map<LocalDate, ApiSecurityPaperHistory> getRecordedHistoryByDate(@NonNull final ApiSecurityPaper securityPaper) {
        return apiSecurityPaperHistoryService.findByAsset(securityPaper)
                                             .stream()
                                             .filter(history -> HistoryType.RECORDED.equals(history.getHistoryType()))
                                             .collect(Collectors.toMap(ApiSecurityPaperHistory::getHistoryDate, Function.identity()));
    }

    private void migrateHistory(ApiSecurityPaper securityPaperToMerge, ApiSecurityPaper targetSecurityPaper) {
        Map<HistoryRun, ApiSecurityPaperHistory> historyMap = apiSecurityPaperHistoryRepository.findByApiSecurityPaper(targetSecurityPaper)
                                                                                               .stream()
                                                                                               .collect(Collectors.toMap(ApiSecurityPaperHistory::getHistoryRun,
                                                                                                               Function.identity()));

        apiSecurityPaperHistoryRepository.findByApiSecurityPaper(securityPaperToMerge).forEach(sourceHistory -> {
            ApiSecurityPaperHistory targetHistory = historyMap.get(sourceHistory.getHistoryRun());
            if (targetHistory != null) {
                // target history already exists
                if (shouldPreferSource(sourceHistory.getHistoryType(), targetHistory.getHistoryType())) {
                    // need to delete first (and flush) as otherwise we have two history records for the same security paper and same history run
                    apiSecurityPaperHistoryRepository.delete(targetHistory);
                    apiSecurityPaperHistoryRepository.flush();
                    sourceHistory.setApiSecurityPaper(targetSecurityPaper);
                    apiSecurityPaperHistoryRepository.save(sourceHistory);
                }
            } else {
                // target history does not exist yet, so reuse the source
                sourceHistory.setApiSecurityPaper(targetSecurityPaper);
                apiSecurityPaperHistoryRepository.save(sourceHistory);
            }
        });
        apiSecurityPaperHistoryRepository.flush();
        apiSecurityPaperHistoryRepository.deleteInBulkByApiSecurityPaper(securityPaperToMerge);
    }

    private boolean shouldPreferSource(final HistoryType sourceHistoryType, final HistoryType targetHistoryType) {
        // order of HistoryType: RECORDED > MANUAL > CALCULATED > AUTOFILL
        if (HistoryType.RECORDED.equals(targetHistoryType)) {
            return false;
        }
        if (HistoryType.RECORDED.equals(sourceHistoryType)) {
            return true;
        }
        if (HistoryType.MANUAL.equals(targetHistoryType)) {
            return false;
        }
        if (HistoryType.MANUAL.equals(sourceHistoryType)) {
            return true;
        }
        if (HistoryType.CALCULATED.equals(targetHistoryType)) {
            return false;
        }
        return HistoryType.CALCULATED.equals(sourceHistoryType);
    }

    private void deleteTransactionsBasedCalculatedOrManualOrAutofillHistory(final ApiSecurityPaper targetSecurityPaper) {
        boolean targetIsTransactionSupported = ApiType.B2B.equals(targetSecurityPaper.getApiType()) //
                        && ((B2BApiSource) targetSecurityPaper.getApiAccount().getApiSource()).getB2bBank().isTransactionsSupported();
        if (targetIsTransactionSupported) {
            log.info("Delete transaction based calculated history for {}", targetSecurityPaper);
            apiSecurityPaperHistoryWriteService.deleteInBulkByApiSecurityPaperAndHistoryTypeIsCalculatedOrManualOrAutofill(targetSecurityPaper);
        }
    }

    private void migrateExpenseOrIncomes(ApiSecurityPaper securityPaperToMerge, ApiSecurityPaper targetSecurityPaper) {
        List<ApiSecurityPaperTransaction> transactionsOfTargetPaper = apiSecurityPaperTransactionRepository.findByApiSecurityPaper(targetSecurityPaper);

        List<ExpenseOrIncome> expenseOrIncomes = expenseOrIncomeRepository.findByApiSecurityPaper(targetSecurityPaper);
        List<ExpenseOrIncome> expensesOfPaperToMerge = expenseOrIncomeRepository.findByApiSecurityPaper(securityPaperToMerge);
        if (!expensesOfPaperToMerge.isEmpty()) {
            log.info("Migrate ExpenseOrIncomes for target={} - securityPaperToMerge={}", targetSecurityPaper, securityPaperToMerge);
            expensesOfPaperToMerge.forEach(expenseOrIncomeToMerge -> {
                boolean expenseOrIncomePresent = expenseOrIncomes.stream().anyMatch(tagetExpenseOrIncome -> //
                                tagetExpenseOrIncome.getThirdPartyPrimaryKey().equals(expenseOrIncomeToMerge.getThirdPartyPrimaryKey()));
                if (!expenseOrIncomePresent) {
                    log.info("Move ExpenseOrIncome {} to {}", expenseOrIncomeToMerge, targetSecurityPaper);
                    if (expenseOrIncomeToMerge.getTransaction() != null) {
                        Optional<ApiSecurityPaperTransaction> toTransaction = transactionsOfTargetPaper.stream().filter(transaction -> //
                                        transaction.getThirdPartyPrimaryKey().equals(expenseOrIncomeToMerge.getTransaction().getThirdPartyPrimaryKey())).findFirst();
                        toTransaction.ifPresent(expenseOrIncomeToMerge::setTransaction);
                    }

                    expenseOrIncomeToMerge.setApiSecurityPaper(targetSecurityPaper);
                    expenseOrIncomeRepository.save(expenseOrIncomeToMerge);
                }
            });
            expenseOrIncomeRepository.flush();
            expenseOrIncomeRepository.deleteInBulkByApiSecurityPaper(securityPaperToMerge);
        }
    }

    private void migrateTransactions(ApiSecurityPaper securityPaperToMerge, ApiSecurityPaper targetSecurityPaper) {
        List<ApiSecurityPaperTransaction> transactionsOfTargetPaper = apiSecurityPaperTransactionRepository.findByApiSecurityPaper(targetSecurityPaper);
        List<ApiSecurityPaperTransaction> transactionsOfPaperToMerge = apiSecurityPaperTransactionRepository.findByApiSecurityPaper(securityPaperToMerge);
        if (!transactionsOfPaperToMerge.isEmpty()) {
            log.info("Migrate Transactions for target={} - securityPaperToMerge={}", targetSecurityPaper, securityPaperToMerge);
            transactionsOfPaperToMerge.forEach(transactionToMerge -> {
                boolean transactionPresent = transactionsOfTargetPaper.stream().anyMatch(targetTransaction -> //
                                targetTransaction.getThirdPartyPrimaryKey().equals(transactionToMerge.getThirdPartyPrimaryKey()));
                if (!transactionPresent) {
                    log.info("Move Transaction {} to {}", transactionToMerge, targetSecurityPaper);
                    transactionToMerge.setApiSecurityPaper(targetSecurityPaper);
                    apiSecurityPaperTransactionRepository.save(transactionToMerge);
                }
            });
            apiSecurityPaperTransactionRepository.flush();
            apiSecurityPaperTransactionRepository.deleteInBulkByApiSecurityPaper(securityPaperToMerge);
        }
    }
}
