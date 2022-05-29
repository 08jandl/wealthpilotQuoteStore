package com.wealthpilot.database.liquibase;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import com.wealthpilot.asset.ApiType;
import com.wealthpilot.asset.HistoryRun;
import com.wealthpilot.asset.HistoryType;
import com.wealthpilot.asset.account.AccountAssetAssignmentRepository;
import com.wealthpilot.asset.account.ApiAccount;
import com.wealthpilot.asset.account.ApiAccountBreakdownRepository;
import com.wealthpilot.asset.account.ApiAccountHistory;
import com.wealthpilot.asset.account.ApiAccountHistoryRepository;
import com.wealthpilot.asset.account.ApiAccountIdentifierProperties;
import com.wealthpilot.asset.account.ApiAccountIdentifierPropertiesCache;
import com.wealthpilot.asset.account.ApiAccountMinimalProperties;
import com.wealthpilot.asset.account.ApiAccountRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperHistoryRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperRepository;
import com.wealthpilot.asset.source.ApiSource;
import com.wealthpilot.asset.source.ApiSourceRepository;

import lombok.extern.log4j.Log4j2;

/**
 * Create demo users for all existing counselors.
 */
@Log4j2
public class DuplicateAccountMigrationTaskChange extends AbstractAsyncTaskChange {

    private ApiSourceRepository apiSourceRepository;
    private ApiAccountRepository apiAccountRepository;
    private ApiAccountHistoryRepository apiAccountHistoryRepository;
    private ApiSecurityPaperRepository apiSecurityPaperRepository;
    private ApiSecurityPaperHistoryRepository apiSecurityPaperHistoryRepository;
    private ApiAccountBreakdownRepository apiAccountBreakdownRepository;
    private AccountAssetAssignmentRepository accountAssetAssignmentRepository;
    private long countMerge = 0;
    private long countNoMerge = 0;

    @Override
    protected void executeAsyncInTransaction() {
        log.info("Migration DuplicateAccountMigrationTaskChange start");
        apiSourceRepository = getBean(ApiSourceRepository.class);
        apiAccountRepository = getBean(ApiAccountRepository.class);
        apiAccountHistoryRepository = getBean(ApiAccountHistoryRepository.class);
        apiSecurityPaperRepository = getBean(ApiSecurityPaperRepository.class);
        apiSecurityPaperHistoryRepository = getBean(ApiSecurityPaperHistoryRepository.class);
        apiAccountBreakdownRepository = getBean(ApiAccountBreakdownRepository.class);
        accountAssetAssignmentRepository = getBean(AccountAssetAssignmentRepository.class);

        apiSourceRepository.findAll().stream().filter(source -> !ApiType.B2B.equals(source.getApiType())).forEach(this::migrateApiSource);
        apiSourceRepository.flush();
        log.info("Migration DuplicateAccountMigrationTaskChange done");
    }

    private void migrateApiSource(final ApiSource apiSource) {
        MigrationApiAccountIdentifierPropertiesCache existingAccountsCache = new MigrationApiAccountIdentifierPropertiesCache();
        apiAccountRepository.findApiAccountMinimalProperties(apiSource.getId()).forEach(account -> {
            Optional<ApiAccountIdentifierProperties> matchingAccount = existingAccountsCache.get(account);
            if (matchingAccount.isPresent()) {
                existingAccountsCache.remove(matchingAccount.get()); // remove previous account from cache (account number and iban)
                ApiAccountIdentifierProperties accountToKeep = mergeAccounts(account, matchingAccount.get());
                existingAccountsCache.add(accountToKeep);
                log.info("Merged: {}, not merged {}", countMerge, countNoMerge);
            } else {
                existingAccountsCache.add(account);
            }
        });
    }

    private ApiAccountIdentifierProperties mergeAccounts(final ApiAccountMinimalProperties accountPropertiesA,
        final ApiAccountIdentifierProperties accountPropertiesB) {
        ApiAccount accountA = Optional.ofNullable(accountPropertiesA.getId()).flatMap(apiAccountRepository::findById).orElseThrow();
        ApiAccount accountB = Optional.ofNullable(accountPropertiesB.getId()).flatMap(apiAccountRepository::findById).orElseThrow();
        log.info("Trying to merge account {} with {}", accountPropertiesA, accountPropertiesA);

        if (ApiType.MANUAL.equals(accountA.getApiType()) || ApiType.MANUAL.equals(accountB.getApiType())) {
            log.info("Cannot merge merge manual account {} with {}", accountA, accountB);
            return accountPropertiesA; // do nothing!
        }

        if (ApiAccount.ACCOUNT_TYPE_SECURITIES.equals(accountPropertiesA.getAccountTypeId())) {
            return mergeDepotAccounts(accountPropertiesA, accountPropertiesB, accountA, accountB);
        }

        // all others than depots:
        return mergeAccounts(accountPropertiesA, accountPropertiesB, accountA, accountB);
    }

    @NonNull
    private ApiAccountIdentifierProperties mergeAccounts(final ApiAccountMinimalProperties accountPropertiesA,
        final ApiAccountIdentifierProperties accountPropertiesB, final ApiAccount accountA, final ApiAccount accountB) {
        if (accountA.getCreationDate().isBefore(accountB.getCreationDate()) || hasAccountAssetAssignments(accountA) || hasAccountBreakdowns(accountA)) {
            mergeAccounts(accountA, accountB);
            return accountPropertiesA;
        } else {
            mergeAccounts(accountB, accountA);
            return accountPropertiesB;
        }
    }

    @NonNull
    private ApiAccountIdentifierProperties mergeDepotAccounts(final ApiAccountMinimalProperties accountPropertiesA,
        final ApiAccountIdentifierProperties accountPropertiesB, final ApiAccount accountA, final ApiAccount accountB) {
        LocalDate saleDateA = accountA.getSaleDate();
        LocalDate saleDateB = accountB.getSaleDate();
        if (saleDateA == null && saleDateB == null) {
            log.info("Trying to merge depots that are both not sold: {}, {}", accountA, accountB);
            if (apiSecurityPaperRepository.countAllByApiAccount(accountA) > 0) {
                mergeDepotAccounts(accountA, accountB);
                return accountPropertiesA;
            }
            mergeDepotAccounts(accountB, accountA);
            return accountPropertiesB;
        } else if (saleDateA != null && saleDateB == null) {
            mergeDepotAccounts(accountB, accountA);
            return accountPropertiesB;
        } else if (saleDateA == null && saleDateB != null) {
            mergeDepotAccounts(accountA, accountB);
            return accountPropertiesA;
        } else if (saleDateA != null && saleDateB != null && saleDateA.isAfter(saleDateB)) {
            mergeDepotAccounts(accountA, accountB);
            return accountPropertiesA;
        } else {
            mergeDepotAccounts(accountB, accountA);
            return accountPropertiesB;
        }
    }

    private void mergeDepotAccounts(final ApiAccount depotToKeep, final ApiAccount depotToEliminate) {
        // check depotToEliminate has no security-papers and no referencing security-paper-history
        if (apiSecurityPaperRepository.countAllByApiAccount(depotToEliminate) > 0) {
            log.warn("Cannot merge depots with security-papers: {}, {}", depotToKeep, depotToEliminate);
            return;
        }
        if (apiSecurityPaperHistoryRepository.countAllByApiAccount(depotToEliminate) > 0) {
            log.warn("Cannot merge depots with security-paper-history: {}, {}", depotToKeep, depotToEliminate);
            return;
        }
        mergeAccounts(depotToKeep, depotToEliminate);
    }

    private void mergeAccounts(final ApiAccount accountToKeep, final ApiAccount accountToEliminate) {
        log.info("Merging accounts {} {}", accountToKeep, accountToEliminate);
        if (hasAccountBreakdowns(accountToEliminate)) {
            countNoMerge++;
            log.warn("Cannot eliminate account with breakdowns! {}, {}", accountToKeep, accountToEliminate);
            return;
        }
        if (hasAccountAssetAssignments(accountToEliminate)) {
            countNoMerge++;
            log.warn("Cannot eliminate account with asset assignments! {}, {}", accountToKeep, accountToEliminate);
            return;
        }

        // check if recorded history overlaps:
        Map<HistoryRun, ApiAccountHistory> historyMap = apiAccountHistoryRepository.findByApiAccount(accountToKeep)
                                                                                   .stream()
                                                                                   .filter(hist -> HistoryType.RECORDED.equals(hist.getHistoryType()))
                                                                                   .collect(
                                                                                       Collectors.toMap(ApiAccountHistory::getHistoryRun, Function.identity()));

        List<ApiAccountHistory> historyList = apiAccountHistoryRepository.findByApiAccount(accountToEliminate)
                                                                         .stream()
                                                                         .filter(hist -> HistoryType.RECORDED.equals(hist.getHistoryType()))
                                                                         .collect(Collectors.toList());
        AtomicBoolean canMerge = new AtomicBoolean(true);
        historyList.forEach(hist -> {
            ApiAccountHistory recordedHistory = historyMap.get(hist.getHistoryRun());
            if (recordedHistory != null) {
                log.info("Recorded history overlap on {}: {}", accountToKeep, hist.getHistoryRun());
                canMerge.set(false);
            }
        });
        if (!canMerge.get()) {
            countNoMerge++;
            log.warn("Some history records overlap, so merge is aborted! {}, {}", accountToKeep, accountToEliminate);
            return;
        }

        countMerge++;

        // finally move the history from accountToEliminate to accountToKeep:
        log.info("Move {} RECORDED history entries to / from account {} {}", historyList.size(), accountToKeep, accountToEliminate);
        historyList.forEach(hist -> hist.setApiAccount(accountToKeep));
        LocalDate saleDateOfAccountToKeep = accountToKeep.getSaleDate();
        LocalDate saleDateOfAccountToEliminate = accountToEliminate.getSaleDate();
        if (saleDateOfAccountToEliminate == null) {
            accountToKeep.setSaleDate(null);
        } else {
            if (saleDateOfAccountToKeep != null && saleDateOfAccountToEliminate.isAfter(saleDateOfAccountToKeep)) {
                accountToKeep.setSaleDate(saleDateOfAccountToEliminate);
            }
        }

        // eliminate account and its calculated history
        log.info("Delete merged account and CALCULATED history of {}", accountToEliminate);
        apiAccountHistoryRepository.deleteInBulkByApiAccountAndHistoryTypeIsCalculated(accountToEliminate);
        apiAccountRepository.delete(accountToEliminate);
        apiAccountRepository.flush();
    }

    private boolean hasAccountBreakdowns(ApiAccount account) {
        return !apiAccountBreakdownRepository.findByApiAccountOrderByPercentageDesc(account).isEmpty();
    }

    private boolean hasAccountAssetAssignments(ApiAccount account) {
        return accountAssetAssignmentRepository.findByApiAccount(account) != null;
    }

    /**
     * Cache having no business logic with sale date on adding an account.
     */
    static class MigrationApiAccountIdentifierPropertiesCache extends ApiAccountIdentifierPropertiesCache {
        @Override
        protected void addAccountWithId(@NonNull final ApiAccountIdentifierProperties account, final String newAccountId) {
            // just add it, no check of sale dates as in base class:
            doPutIntoCache(account, newAccountId);
        }
    }
}
