package com.wealthpilot.database.liquibase;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.lang.NonNull;
import com.wealthpilot.asset.account.ApiAccount;
import com.wealthpilot.asset.account.ApiAccountHistory;
import com.wealthpilot.asset.account.ApiAccountHistoryRepository;
import com.wealthpilot.asset.account.ApiAccountRepository;
import com.wealthpilot.asset.source.ApiSourceRepository;
import com.wealthpilot.asset.source.B2BApiSource;
import com.wealthpilot.b2b.B2BBank;

import lombok.extern.log4j.Log4j2;

/**
 * One-time migration task, to be deleted after execution!
 */
@Log4j2
public class InvalidAABAccountHistoryMigrationTaskChange extends AbstractAsyncTaskChange {

    private ApiAccountHistoryRepository apiAccountHistoryRepository;

    private static final Set<Long> ACCOUNTS_WITH_INVALID_END_VALUE = Set.of(1649962596L, 631L);

    @Override
    protected void executeAsyncInTransaction() {
        log.info("Migration InvalidAABAccountHistoryMigrationTaskChange start");
        final ApiSourceRepository apiSourceRepository = getBean(ApiSourceRepository.class);
        final ApiAccountRepository apiAccountRepository = getBean(ApiAccountRepository.class);
        apiAccountHistoryRepository = getBean(ApiAccountHistoryRepository.class);

        apiSourceRepository.findAll()
                           .stream()
                           .filter(B2BApiSource.class::isInstance)
                           .map(B2BApiSource.class::cast)
                           .filter(source -> B2BBank.AAB.equals(source.getB2bBank()))
                           .flatMap(source -> apiAccountRepository.findAllByApiSource(source).stream())
                           .filter(account -> !ApiAccount.ACCOUNT_TYPE_SECURITIES.equals(account.getAccountTypeId()))
                           .forEach(this::migrateApiAccount);

        apiSourceRepository.flush();
        log.info("Migration InvalidAABAccountHistoryMigrationTaskChange done");
    }

    private void migrateApiAccount(@NonNull final ApiAccount apiAccount) {
        LocalDate lastKnownCorrectDate = LocalDate.of(2020, 10, 27);
        List<ApiAccountHistory> accountHistory =
            apiAccountHistoryRepository.findByApiAccountFromStartDateToEndDate(apiAccount, lastKnownCorrectDate, LocalDate.now());

        if (accountHistory.isEmpty()) {
            log.info("No history to migrate for account name={}, saleDate={}: {}", apiAccount.getName(), apiAccount.getSaleDate(), apiAccount);
            Optional<Long> lastHistoryId =
                apiAccountHistoryRepository.findIDsByAssetFromStartDate(apiAccount, LocalDate.of(1990, 1, 1)).reduce((first, second) -> second);
            Optional<ApiAccountHistory> apiAccountHistory = lastHistoryId.flatMap(apiAccountHistoryRepository::findById);
            if (apiAccountHistory.isPresent() && !Objects.equals(apiAccount.getName(), apiAccountHistory.get().getName())) {
                log.info("Account name today is different than in last history: {} vs. {} in {}", apiAccount.getName(), apiAccountHistory.get().getName(),
                    apiAccount);
                ApiAccountHistory lastHistory = apiAccountHistory.get();
                apiAccount.setValue(lastHistory.getValue());
                apiAccount.setThirdPartyValue(lastHistory.getThirdPartyValue());
                apiAccount.setCurrency(lastHistory.getCurrency());
                apiAccount.setName(lastHistory.getName());
                apiAccount.setCustomerName(lastHistory.getCustomerName());
            }
            return;
        }

        ApiAccountHistory firstHistory = accountHistory.get(0);
        LocalDate firstHistoryDate = firstHistory.getHistoryDate();
        if (firstHistoryDate != null && firstHistoryDate.isAfter(lastKnownCorrectDate)) {
            log.info("First history-date is within affected period, name={}, saleDate={}: {}", firstHistory.getName(), apiAccount.getSaleDate(), firstHistory);
        }

        if (ACCOUNTS_WITH_INVALID_END_VALUE.contains(apiAccount.getId())) {
            log.info("Fixing values today for account {}", apiAccount);
            apiAccount.setValue(firstHistory.getValue());
            apiAccount.setThirdPartyValue(firstHistory.getThirdPartyValue());
            apiAccount.setCurrency(firstHistory.getCurrency());
            apiAccount.setName(firstHistory.getName());
            apiAccount.setCustomerName(firstHistory.getCustomerName());
        }

        String accountNameBefore = firstHistory.getName();
        String accountNameToday = apiAccount.getName();
        boolean accountNameChanged = !Objects.equals(accountNameBefore, accountNameToday);
        if (accountNameChanged) {
            log.info("Account-name before the bug is different to account-name today: {} vs. {} for {}", accountNameBefore, accountNameToday, apiAccount);
        }

        ApiAccountHistory previous = firstHistory;
        for (ApiAccountHistory history : accountHistory) {
            String historyName = history.getName();
            if (!Objects.equals(accountNameBefore, historyName) && !Objects.equals(accountNameToday, historyName)) {
                log.info("Fixing history values because of name change: '{}' vs. '{}' vs. '{}' for {}", accountNameBefore, historyName, accountNameToday,
                    history);
                history.setValue(previous.getValue());
                history.setThirdPartyValue(previous.getThirdPartyValue());
                history.setCurrency(previous.getCurrency());
                history.setName(previous.getName());
                history.setCustomerName(previous.getCustomerName());
            }
            previous = history;
        }
    }
}
