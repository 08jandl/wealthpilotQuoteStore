package com.wealthpilot.b2b.download.sftp.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.wealthpilot.asset.account.ApiAccountTransactionRepository;
import com.wealthpilot.asset.securitypaper.ApiSecurityPaperTransactionRepository;
import com.wealthpilot.asset.securitypaper.ExpenseOrIncomeRepository;
import com.wealthpilot.asset.source.ApiSource;
import com.wealthpilot.b2b.B2BBank;
import com.wealthpilot.b2b.FileDownload;
import com.wealthpilot.b2b.FileDownloadRepository;
import com.wealthpilot.b2b.service.B2BApiSource;
import com.wealthpilot.b2b.service.B2BApiSourceRepository;
import com.wealthpilot.migration.AbstractMigrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * This service sets the deletes all duplicate FileDownloads from Atruvia(Fiducia).
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
public class FiduciaDuplicateFileDownloadMigrationService extends AbstractMigrationService<Long> {
    private final B2BApiSourceRepository b2BApiSourceRepository;
    private final FileDownloadRepository fileDownloadRepository;
    private final ApiAccountTransactionRepository apiAccountTransactionRepository;
    private final ApiSecurityPaperTransactionRepository apiSecurityPaperTransactionRepository;
    private final ExpenseOrIncomeRepository expenseOrIncomeRepository;
    protected static final Comparator<FileDownload> FILE_DOWNLOAD_COMPARATOR =
                    Comparator.comparing(FileDownload::isSuccessfullyProcessed).thenComparing(FileDownload::getId).reversed();


    @Override
    @NonNull
    protected List<Long> getAllIdsToProcess() {
        return b2BApiSourceRepository.findAllByB2bBank(B2BBank.FIDUCIA).map(ApiSource::getId).collect(Collectors.toList());
    }

    @Override
    @NonNull
    protected Stream<Boolean> migrateBatch(@NonNull final List<Long> ids) {
        return b2BApiSourceRepository.findAllById(ids).stream().map(this::findAndHandleDuplicateFileDownloads);
    }

    private boolean findAndHandleDuplicateFileDownloads(final B2BApiSource b2BApiSource) {
        final Collection<Collection<FileDownload>> fileDownloadsGroupedByName = getFileDownloadsGroupedByName(b2BApiSource);
        final AtomicBoolean migratedDuplicates = new AtomicBoolean(false);
        fileDownloadsGroupedByName.stream()
                                  .filter(fileDownloads -> fileDownloads.size() > 1)
                                  .forEach(fileDownloads -> migrateDuplicateFileDownloads(fileDownloads, migratedDuplicates));
        return migratedDuplicates.get();
    }

    private void migrateDuplicateFileDownloads(final Collection<FileDownload> fileDownloadsWithDuplicateNames, final AtomicBoolean isMigrated) {
        if (checkIfAllFileDownloadsHaveEqualSize(fileDownloadsWithDuplicateNames)) {
            log.info("There are file downloads with equal name and size, deleting all but oldest: {}", fileDownloadsWithDuplicateNames);
            isMigrated.set(deleteAllButOneDuplicateFileDownloads(fileDownloadsWithDuplicateNames));
        } else {
            log.warn("There are file downloads with equal name but unequal sizes {}", fileDownloadsWithDuplicateNames);
        }
    }

    @NonNull
    private Collection<Collection<FileDownload>> getFileDownloadsGroupedByName(final B2BApiSource b2BApiSource) {
        List<FileDownload> fileDownloads = fileDownloadRepository.findAllBySourceOrderById(b2BApiSource);
        Multimap<String, FileDownload> duplicateFileDownloads = ArrayListMultimap.create();
        fileDownloads.forEach(fileDownload -> duplicateFileDownloads.put(fileDownload.getFileName(), fileDownload));
        return duplicateFileDownloads.asMap().values();
    }

    private boolean deleteAllButOneDuplicateFileDownloads(final Collection<FileDownload> fileDownloadsToDelete) {
        FileDownload fileDownloadToKeep = fileDownloadsToDelete.stream().min(FILE_DOWNLOAD_COMPARATOR).orElseThrow();
        fileDownloadsToDelete.remove(fileDownloadToKeep);
        log.info("Keeping preferred FileDownload: {}, deleting: {}", fileDownloadToKeep, fileDownloadsToDelete);
        fileDownloadRepository.flush(); // flush before executing modifying queries
        apiAccountTransactionRepository.moveFileDownloadReference(fileDownloadToKeep, fileDownloadsToDelete);
        apiSecurityPaperTransactionRepository.moveFileDownloadReference(fileDownloadToKeep, fileDownloadsToDelete);
        expenseOrIncomeRepository.moveFileDownloadReference(fileDownloadToKeep, fileDownloadsToDelete);
        fileDownloadRepository.deleteAllInBatch(fileDownloadsToDelete);
        return true;
    }

    private boolean checkIfAllFileDownloadsHaveEqualSize(final Collection<FileDownload> fileDownloadsWithDuplicateNames) {
        return fileDownloadsWithDuplicateNames.stream().map(FileDownload::getContentLength).distinct().count() == 1;
    }

    @Override
    @NonNull
    protected Logger getLogger() {
        return log;
    }

    @Override
    public String getName() {
        return "Fiducia duplicate file download";
    }
}
