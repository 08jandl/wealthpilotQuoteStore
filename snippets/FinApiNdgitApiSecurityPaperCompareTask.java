package com.wealthpilot.database.liquibase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.persistence.EntityManager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.wealthpilot.domain.ApiAccount;
import com.wealthpilot.domain.ApiSource;
import com.wealthpilot.domain.ApiUser;
import com.wealthpilot.service.ApiUserService;
import com.wealthpilot.service.finapi.FinApiSecurityPaperService;
import com.wealthpilot.finapi.dto.FinApiSecurityPaper;
import com.wealthpilot.service.ndgit.AbstractNdgitService;
import com.wealthpilot.service.ndgit.NdgitApiAccountService;
import com.wealthpilot.service.ndgit.dto.NdgitSecurityPaper;
import com.wealthpilot.web.rest.dto.ApiSecurityPaperDTO;
import com.wealthpilot.web.rest.mapper.ApiSecurityPaperMapper;

/**
 * Migrate all api sources and fill them with finApi interface definitions.
 * This is a one-time migration and should be deleted in the next release!
 */
public class FinApiNdgitApiSecurityPaperCompareTask extends AbstractBeanProcessingTaskChange {

    private final Logger log = LogManager.getLogger(FinApiNdgitApiSecurityPaperCompareTask.class);

    private FinApiSecurityPaperService finApiSecurityPaperService;
    private NdgitApiAccountService ndgitApiAccountService;
    private ApiUserService apiUserService;
    private ApiSecurityPaperMapper mapper;

    @Override
    protected void executeInTransaction() {
        EntityManager entityManager = getBean(EntityManager.class);
        apiUserService = getBean(ApiUserService.class);
        finApiSecurityPaperService = getBean(FinApiSecurityPaperService.class);
        ndgitApiAccountService = getBean(NdgitApiAccountService.class);
        mapper = getBean(ApiSecurityPaperMapper.class);

        log.info("Counting ApiSecurityPapers ndgit migration...");
        long startTime = System.currentTimeMillis();

        Stream<ApiAccount> resultStream = entityManager.createQuery(
            "SELECT account "
                + " FROM ApiAccount account "
                + " WHERE account.apiType in (com.wealthpilot.asset.ApiType.FIN_API, com.wealthpilot.asset.ApiType.NDGIT)"
                + " AND account.accountTypeId = '4'" // only depots
                + " AND exists ("
                + "     SELECT paper "
                + "     FROM ApiSecurityPaper paper "
                + "     WHERE paper.apiAccount = account )", ApiAccount.class).getResultStream();

        final Set<String> banksTested = new HashSet<>();
        resultStream.forEach(apiAccount -> {
            ApiSource apiSource = apiAccount.getApiSource();
            String bankName = apiSource.getBankName();
            if (banksTested.contains(bankName)) {
                log.info("Bank already tested: {}", bankName);
            } else {
                log.info("Fetching security papers for apiSource {}", bankName);
                banksTested.add(bankName);

                List<ApiSecurityPaperDTO> finApiDtos = new ArrayList<>();
                boolean finApiException = false;
                try {
                    ApiUser apiUser = apiUserService.getApiUser(apiSource);
                    List<FinApiSecurityPaper> finApiPapers = finApiSecurityPaperService.getSecurityPapersForAccount(apiUser, apiAccount);
                    finApiDtos = mapper.finApiSecurityPapersToApiSecurityPaperDTOs(finApiPapers);
                } catch (Exception e) {
                    log.info("FinApi threw an exception on request for security papers: {}, {}, {}", bankName, apiAccount, e.getMessage());
                    finApiException = true;
                }

                List<ApiSecurityPaperDTO> ndgitDtos = new ArrayList<>();
                boolean ndgitException = false;
                try {
                    AbstractNdgitService.NdgitAuthenticationWrapper ndgitAuth = ndgitApiAccountService.createNdgitBankAuthentication(apiSource);
                    NdgitSecurityPaper[] ndgitPapers =
                        ndgitApiAccountService.getApiSecurityPapers(apiSource.getBankBlz(), ndgitAuth.getUsername(), ndgitAuth.getUsername2(),
                            ndgitAuth.getPin(), apiAccount.getAccountNumber(), apiAccount.getSubAccountNumber());
                    ndgitDtos = mapper.ndgitSecurityPapersToApiSecurityPaperDTOs(Arrays.asList(ndgitPapers));
                } catch (Exception e) {
                    log.info("Ndgit threw an exception on request for security papers: {}, {}, {}", bankName, apiAccount, e.getMessage());
                    ndgitException = true;
                }

                if (!finApiException && !ndgitException && ndgitDtos.size() == 0 && finApiDtos.size() == 0) {
                    banksTested.remove(bankName); // give it another try on the next bank!
                }
                if (finApiException && ndgitException) {
                    banksTested.remove(bankName); // give it another try on the next bank!
                }
                log.info("Bank Comparison;FinApi#;{};Ndgit#;{};finApiException;{};ndgitException;{};Bank;{}", finApiDtos.size(), ndgitDtos.size(),
                    finApiException, ndgitException, bankName);
            }
        });

        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        log.info("Migration of ApiSources done in {} seconds", elapsedTime);
    }

}
