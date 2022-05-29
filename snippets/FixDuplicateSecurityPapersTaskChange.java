package com.wealthpilot.database.liquibase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Assert;
import com.wealthpilot.asset.ApiType;
import com.wealthpilot.asset.HistoryRun;
import com.wealthpilot.asset.account.ApiAccount;
import com.wealthpilot.asset.account.ApiAccountRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaper;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperHistory;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperHistoryRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperHistoryService;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperTransaction;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperTransactionRepository;
import com.wealthpilot.asset.securitypaper.ExpenseOrIncome;
import com.wealthpilot.asset.securitypaper.ExpenseOrIncomeRepository;
import com.wealthpilot.asset.source.B2BApiSource;
import com.wealthpilot.usermanagement.user.UserServiceFacade;

import lombok.extern.log4j.Log4j2;

/**
 * @author PhilippSommersguter
 **/
@Log4j2
public class FixDuplicateSecurityPapersTaskChange extends AbstractAsyncTaskChange {

    private ApiSecurityPaperHistoryRepository apiSecurityPaperHistoryRepository;
    private ApiSecurityPaperTransactionRepository apiSecurityPaperTransactionRepository;
    private ExpenseOrIncomeRepository expenseOrIncomeRepository;

    @Override
    protected void executeAsyncInTransaction() {
        log.info("Starting FixDuplicateSecurityPapersTaskChange");
        apiSecurityPaperHistoryRepository = getBean(ApiSecurityPaperHistoryRepository.class);
        apiSecurityPaperTransactionRepository = getBean(ApiSecurityPaperTransactionRepository.class);
        expenseOrIncomeRepository = getBean(ExpenseOrIncomeRepository.class);

        final ApiSecurityPaperRepository apiSecurityPaperRepository = getBean(ApiSecurityPaperRepository.class);
        final UserService userService = getBean(UserService.class);

        final Long systemUserId = userService.getSystemUser().getId();

        Assert.notNull(systemUserId, "The system user must exist and have a non null ID.");
        userService.switchUser(systemUserId, null, null, () -> {
            AtomicInteger accountCounter = new AtomicInteger();
            final List<ApiAccount> allApiAccounts = getBean(ApiAccountRepository.class).findAll();
            allApiAccounts.forEach(apiAccount -> {
                List<ApiSecurityPaper> securityPapers = apiSecurityPaperRepository.findAllByApiAccount(apiAccount);

                List<ApiSecurityPaper> knownMergedInstances = new ArrayList<>();

                securityPapers.forEach(targetSecurityPaper -> {
                    if (knownMergedInstances.contains(targetSecurityPaper)) {
                        return;
                    }

                    Optional<ApiSecurityPaper> securityPaperToMergeOptional = findMatchingSoldSecurityPaper(targetSecurityPaper, securityPapers);
                    if (securityPaperToMergeOptional.isPresent()) {
                        ApiSecurityPaper securityPaperToMerge = securityPaperToMergeOptional.get();
                        if (knownMergedInstances.contains(securityPaperToMerge)) {
                            log.warn("Unexpectedly found already merged paper {} as match for {}", securityPaperToMerge, targetSecurityPaper);
                            return;
                        }

                        migrateHistory(securityPaperToMerge, targetSecurityPaper);
                        migrateExpenseOrIncomes(securityPaperToMerge, targetSecurityPaper);
                        migrateTransactions(securityPaperToMerge, targetSecurityPaper);
                        targetSecurityPaper.setAcquisitionDate(securityPaperToMerge.getAcquisitionDate());
                        apiSecurityPaperRepository.save(targetSecurityPaper);
                        apiSecurityPaperRepository.delete(securityPaperToMerge);
                        apiSecurityPaperRepository.flush();
                        getBean(ApiSecurityPaperHistoryService.class).fillHistoryAndCalculateRateOfReturnData(targetSecurityPaper);
                        knownMergedInstances.add(securityPaperToMerge);
                    }
                });
                log.info("Finished migrating account {}/{} where we merged {} security papers", accountCounter.incrementAndGet(), allApiAccounts.size(),
                    knownMergedInstances.size());
            });
        });

        log.info("Finished FixDuplicateSecurityPapersTaskChange");
    }

    private void migrateExpenseOrIncomes(ApiSecurityPaper fromSecurityPaper, ApiSecurityPaper toSecurityPaper) {
        List<ApiSecurityPaperTransaction> transactionsOfToPaper = apiSecurityPaperTransactionRepository.findByApiSecurityPaper(toSecurityPaper);

        List<ExpenseOrIncome> expenseOrIncomes = expenseOrIncomeRepository.findByApiSecurityPaper(toSecurityPaper);
        expenseOrIncomeRepository.findByApiSecurityPaper(fromSecurityPaper).forEach(expenseOrIncome -> {
            boolean expenseOrIncomePresent = expenseOrIncomes.stream().anyMatch(expenseOrIncome1 -> //
                expenseOrIncome1.getThirdPartyPrimaryKeyHash().equals(expenseOrIncome.getThirdPartyPrimaryKeyHash()));
            if (!expenseOrIncomePresent) {
                if (expenseOrIncome.getTransaction() != null) {
                    Optional<ApiSecurityPaperTransaction> toTransaction = transactionsOfToPaper.stream().filter(transaction -> //
                        transaction.getUniqueKeyForOneAccount().equals(expenseOrIncome.getTransaction().getUniqueKeyForOneAccount())).findFirst();
                    toTransaction.ifPresent(expenseOrIncome::setTransaction);
                }

                expenseOrIncome.setApiSecurityPaper(toSecurityPaper);
                expenseOrIncomeRepository.save(expenseOrIncome);
            }
        });
        expenseOrIncomeRepository.flush();
        expenseOrIncomeRepository.deleteInBulkByApiSecurityPaper(fromSecurityPaper);
    }

    private void migrateTransactions(ApiSecurityPaper fromSecurityPaper, ApiSecurityPaper toSecurityPaper) {
        List<ApiSecurityPaperTransaction> transactionsOfToPaper = apiSecurityPaperTransactionRepository.findByApiSecurityPaper(toSecurityPaper);

        apiSecurityPaperTransactionRepository.findByApiSecurityPaper(fromSecurityPaper).forEach(apiSecurityPaperTransaction -> {
            boolean transactionPresent = transactionsOfToPaper.stream().anyMatch(apiSecurityPaperTransaction1 -> //
                apiSecurityPaperTransaction1.getUniqueKeyForOneAccount().equals(apiSecurityPaperTransaction.getUniqueKeyForOneAccount()));
            if (!transactionPresent) {
                apiSecurityPaperTransaction.setApiSecurityPaper(toSecurityPaper);
                apiSecurityPaperTransactionRepository.save(apiSecurityPaperTransaction);
            }
        });
        apiSecurityPaperTransactionRepository.flush();
        apiSecurityPaperTransactionRepository.deleteInBulkByApiSecurityPaper(fromSecurityPaper);
    }

    private void migrateHistory(ApiSecurityPaper fromSecurityPaper, ApiSecurityPaper toSecurityPaper) {
        Set<HistoryRun> targetPaperHistorySet = apiSecurityPaperHistoryRepository.findByApiSecurityPaper(toSecurityPaper)
                                                                                 .stream()
                                                                                 .map(ApiSecurityPaperHistory::getHistoryRun)
                                                                                 .collect(Collectors.toSet());

        apiSecurityPaperHistoryRepository.findByApiSecurityPaper(fromSecurityPaper).forEach(apiSecurityPaperHistory -> {
            if (!targetPaperHistorySet.contains(apiSecurityPaperHistory.getHistoryRun())) {
                apiSecurityPaperHistory.setApiSecurityPaper(toSecurityPaper);
                apiSecurityPaperHistoryRepository.save(apiSecurityPaperHistory);
            }
        });
        apiSecurityPaperHistoryRepository.flush();
        apiSecurityPaperHistoryRepository.deleteInBulkByApiSecurityPaper(fromSecurityPaper);

        boolean targetIsTransactionSupported = ApiType.B2B.equals(toSecurityPaper.getApiType()) && //
            ((B2BApiSource) toSecurityPaper.getApiAccount().getApiSource()).getB2bBank().isTransactionsSupported();
        if (targetIsTransactionSupported) {
            apiSecurityPaperHistoryRepository.deleteInBulkByApiSecurityPaperAndHistoryTypeIsCalculated(toSecurityPaper);
            apiSecurityPaperHistoryRepository.flush();
        }
    }

    private Optional<ApiSecurityPaper> findMatchingSoldSecurityPaper(ApiSecurityPaper currentSecurityPaper, List<ApiSecurityPaper> securityPapers) {
        List<ApiSecurityPaper> papers =
            securityPapers.stream().filter(apiSecurityPaper -> matches(apiSecurityPaper, currentSecurityPaper)).collect(Collectors.toList());
        if (papers.size() != 1) {
            if (papers.size() > 1) {
                log.info("Discarding multiple matching security papers {} for {}", Strings.join(papers, ','), currentSecurityPaper);
            }
            return Optional.empty();
        }

        ApiSecurityPaper matchingPaper = papers.get(0);
        if (matchingPaper.equals(currentSecurityPaper)) {
            log.info("Ignoring own instance as match for {}", matchingPaper);
            return Optional.empty();
        }
        return Optional.of(matchingPaper);
    }

    private boolean matches(ApiSecurityPaper securityPaperToMatch, ApiSecurityPaper currentSecurityPaper) {
        if (!securityPaperToMatch.getIsin().equals(currentSecurityPaper.getIsin())) {
            return false;
        }

        LocalDate acquisitionDate = currentSecurityPaper.getAcquisitionDate();
        LocalDate saleDate = securityPaperToMatch.getSaleDate();

        if (saleDate == null || acquisitionDate == null) {
            return false;
        }

        boolean saleDateIsAroundAcquisitionDate = saleDate.isAfter(acquisitionDate.minusDays(2)) && saleDate.isBefore(acquisitionDate.plusDays(2));
        if (!saleDateIsAroundAcquisitionDate) {
            return false;
        }

        StringBuilder messageBuilder =
            new StringBuilder("Found matching security paper ").append(securityPaperToMatch).append(" for ").append(currentSecurityPaper).append("where ");

        final String currency = securityPaperToMatch.getCurrency();
        if (currency != null && !currency.equals(currentSecurityPaper.getCurrency())) {
            messageBuilder.append("currency changed from ").append(currency).append(" to ").append(currentSecurityPaper.getCurrency());
        } else {
            messageBuilder.append("currency changed from null").append(currentSecurityPaper.getCurrency());
        }

        final String quoteCurrency = securityPaperToMatch.getQuoteCurrency();
        if (quoteCurrency != null && quoteCurrency.equals(currentSecurityPaper.getQuoteCurrency())) {
            messageBuilder.append("quoteCurrency changed from ").append(currency).append(" to ").append(currentSecurityPaper.getCurrency());
        } else {
            messageBuilder.append("quoteCurrency changed from null").append(currentSecurityPaper.getQuoteCurrency());
        }

        log.info(messageBuilder.toString());
        return true;
    }
}
