package com.wealthpilot.quote.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.assertj.core.data.Offset;
import org.ff4j.FF4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import com.wealthpilot.quote.store.util.IsinQuote;
import com.wealthpilot.quote.store.util.IsinQuoteHistory;
import com.wealthpilot.quote.store.util.LoggingInterceptor;
import com.wealthpilot.quote.store.util.LoggingInterceptorExtension;
import com.wealthpilot.quote.store.util.Quote;
import com.wealthpilot.quote.store.util.QuoteSource;
import com.wealthpilot.quote.store.util.QuoteType;

import lombok.RequiredArgsConstructor;

@ExtendWith({ MockitoExtension.class, LoggingInterceptorExtension.class })
@RequiredArgsConstructor
class JPAQuoteServiceTest {
    private static final String VALID_ISIN = "DE000A1EWWW0";
    private static final String INVALID_ISIN = "FOO";

    private final LoggingInterceptor loggingInterceptor;

    @Mock
    private JPAQuoteRepository quoteRepository;

    @Mock
    private JPAQuoteIdentifierRepository quoteIdentifierRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private FF4j ff4j;

    private JPAQuoteService jpaQuoteService;
    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<List<JPAQuote>> quotesCaptor = ArgumentCaptor.forClass(List.class);
    private final ArgumentCaptor<JPAQuote> quoteCaptor = ArgumentCaptor.forClass(JPAQuote.class);

    @BeforeEach
    void setUp() {
        jpaQuoteService = new JPAQuoteService(quoteRepository, quoteIdentifierRepository, transactionTemplate, ff4j);
        doAnswer(invocation -> invocation.getArgument(0, JPAQuoteIdentifier.class)).when(quoteIdentifierRepository).save(any());
    }

    @Test
    void getEmptyQuoteHistoryForExistingIsin() {
        // Given:
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        quoteIdentifier.setQuoteSource(QuoteSource.BANK_API);
        quoteIdentifier.setFetchDate(ZonedDateTime.now());
        when(quoteIdentifierRepository.findOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findAllByIdentifier(quoteIdentifier)).thenReturn(List.of());

        // When:
        IsinQuoteHistory quoteHistory = jpaQuoteService.getQuoteHistory(VALID_ISIN);

        // Then:
        assertThat(quoteHistory.getIsin()).isEqualTo(VALID_ISIN);
        loggingInterceptor.assertNoWarnings();
        loggingInterceptor.assertNoInfos();
        verifyNoMoreInteractions(quoteRepository, quoteIdentifierRepository);
    }

    @Test
    void doNotFetchForInvalidIsin() {
        // Given:
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(INVALID_ISIN);
        when(quoteIdentifierRepository.findOneByIsin(INVALID_ISIN)).thenReturn(Optional.empty());
        when(quoteIdentifierRepository.findAndLockOneByIsin(INVALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findAllByIdentifier(quoteIdentifier)).thenReturn(List.of());
        mockTransactionTemplate();

        // When:
        IsinQuoteHistory quoteHistory = jpaQuoteService.getQuoteHistory(INVALID_ISIN);

        // Then:
        assertThat(quoteHistory.getIsin()).isEqualTo(INVALID_ISIN);
        loggingInterceptor.assertNoWarnings();
        loggingInterceptor.assertNoInfos();
        verifyNoMoreInteractions(quoteRepository, quoteIdentifierRepository);
    }

    @Test
    void storeQuoteFromBankIfNoCurrentQuoteAndUpdateIdentifier() {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        assertThat(quoteIdentifier.getQuoteSource()).isEqualTo(QuoteSource.MANUAL);

        IsinQuote quoteFromBank = new IsinQuote("GER", VALID_ISIN, LocalDate.now(), new Quote("EUR", 12.3, LocalDate.now()), QuoteSource.BANK_API, false);

        when(quoteIdentifierRepository.findAndLockOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate())).thenReturn(Optional.empty());
        when(quoteRepository.findFirstByIdentifierOrderByQuoteDateDesc(quoteIdentifier)).thenReturn(Optional.empty());

        jpaQuoteService.storeQuote(quoteFromBank);

        verify(quoteRepository).save(quoteCaptor.capture());
        JPAQuote quote = quoteCaptor.getValue();
        assertThat(quote.getQuoteDate()).isEqualTo(LocalDate.now());
        assertThat(quote.getQuoteAmount()).isCloseTo(12.3, Offset.offset(0.001));
        assertThat(quote.getQuoteCurrency()).isEqualTo("EUR");
        assertThat(quoteIdentifier.getQuoteSource()).isEqualTo(QuoteSource.BANK_API);
        verify(quoteIdentifierRepository, times(2)).save(any());
        verifyNoMoreInteractions(quoteRepository, quoteIdentifierRepository);
    }

    @Test
    void doNotSaveWhenQuoteSourceRefinitiv() {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        quoteIdentifier.setQuoteSource(QuoteSource.REFINITIV_RKD);
        quoteIdentifier.setMarketPlace("n/a");

        IsinQuote quoteFromBank = new IsinQuote("GER", VALID_ISIN, LocalDate.now(), new Quote("EUR", 12.3, LocalDate.now()), QuoteSource.BANK_API, false);

        when(quoteIdentifierRepository.findAndLockOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate())).thenReturn(Optional.empty());
        final JPAQuote quoteFromDb = createQuote(quoteIdentifier);
        when(quoteRepository.findFirstByIdentifierOrderByQuoteDateDesc(quoteIdentifier)).thenReturn(Optional.of(quoteFromDb));

        jpaQuoteService.storeQuote(quoteFromBank);

        verify(quoteIdentifierRepository, times(1)).save(any());
        assertThat(quoteIdentifier.getQuoteSource()).isEqualTo(QuoteSource.REFINITIV_RKD);
    }

    @Test
    void saveBankQuoteAndUpdateIdentifierIfMarketPlaceChanged() {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        quoteIdentifier.setQuoteSource(QuoteSource.BANK_API);

        assertThat(quoteIdentifier.getMarketPlace()).isNotEqualTo("GER");
        IsinQuote quoteFromBank = new IsinQuote("GER", VALID_ISIN, LocalDate.now(), new Quote("EUR", 12.3, LocalDate.now()), QuoteSource.BANK_API, false);

        when(quoteIdentifierRepository.findAndLockOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate())).thenReturn(Optional.empty());

        jpaQuoteService.storeQuote(quoteFromBank);

        assertThat(quoteIdentifier.getQuoteSource()).isEqualTo(QuoteSource.BANK_API);
        assertThat(quoteIdentifier.getMarketPlace()).isEqualTo("GER");
        loggingInterceptor.assertInfo("Market place has changed.*");
    }

    @Test
    void warnIfNoValidQuote() {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        quoteIdentifier.setQuoteSource(QuoteSource.REFINITIV_RKD);
        quoteIdentifier.setMarketPlace("n/a");

        IsinQuote quoteFromBank = new IsinQuote("GER", VALID_ISIN, LocalDate.now(), new Quote("EUR", 12.3, LocalDate.now()), QuoteSource.BANK_API, false);

        when(quoteIdentifierRepository.findAndLockOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate())).thenReturn(Optional.empty());
        final JPAQuote quoteFromDb = createQuote(quoteIdentifier);
        quoteFromDb.setQuoteDate(LocalDate.now().minusYears(1));
        when(quoteRepository.findFirstByIdentifierOrderByQuoteDateDesc(quoteIdentifier)).thenReturn(Optional.of(quoteFromDb));

        jpaQuoteService.storeQuote(quoteFromBank);

        verify(quoteIdentifierRepository, times(1)).save(any());
        loggingInterceptor.assertWarning("No valid quote for.*");
        assertThat(quoteIdentifier.getQuoteSource()).isEqualTo(QuoteSource.REFINITIV_RKD);
    }

    @Test
    void doNotSaveIfAlreadySaved() {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);
        quoteIdentifier.setQuoteSource(QuoteSource.BANK_API);

        IsinQuote quoteFromBank = new IsinQuote("GER", VALID_ISIN, LocalDate.now(), new Quote("EUR", 12.3, LocalDate.now()), QuoteSource.BANK_API, false);

        when(quoteIdentifierRepository.findAndLockOneByIsin(VALID_ISIN)).thenReturn(Optional.of(quoteIdentifier));
        when(quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate())).thenReturn(
                        Optional.of(createQuote(quoteIdentifier)));

        jpaQuoteService.storeQuote(quoteFromBank);

        loggingInterceptor.assertNoWarnings();
        loggingInterceptor.assertNoInfos();
        verify(quoteIdentifierRepository, times(1)).save(any());
    }

    @ParameterizedTest(name = "index => existingQuotes{0}, newQuotes{1}, expectedInfo{2}, expectedWarning{3}, expectedResult{4}")
    @MethodSource("testData")
    void test(List<JPAQuote> existingQuotes, List<JPAQuote> newQuotes, String expectedInfo, String expectedWarning, boolean expectedResult) {
        JPAQuoteIdentifier quoteIdentifier = jpaQuoteService.createIdentifier(VALID_ISIN);

        final boolean shouldReplace = jpaQuoteService.shouldReplaceExistingQuotes(quoteIdentifier, existingQuotes, newQuotes);

        assertThat(shouldReplace).isEqualTo(expectedResult);

        if (expectedInfo.equals("")) {
            loggingInterceptor.assertNoInfos();
        } else {
            loggingInterceptor.assertInfo(expectedInfo);
        }
        if (expectedWarning.equals("")) {
            loggingInterceptor.assertNoWarnings();
        } else {
            loggingInterceptor.assertWarning(expectedWarning);
        }
    }

    private static Stream<Arguments> testData() {
        JPAQuote currentQuote = new JPAQuote();
        currentQuote.setQuoteAmount(1.1);
        currentQuote.setQuoteDate(LocalDate.now());

        JPAQuote quoteYesterday = new JPAQuote();
        quoteYesterday.setQuoteAmount(1.2);
        quoteYesterday.setQuoteDate(LocalDate.now().minusDays(1));

        JPAQuote oldQuote = new JPAQuote();
        oldQuote.setQuoteAmount(1.3);
        oldQuote.setQuoteDate(LocalDate.now().minusDays(10));

        return Stream.of(Arguments.of(List.of(currentQuote), List.of(), "Not replacing existing quotes with empty history.*", "", false),
                        //shouldNotReplaceExistingQuotesWithEmptyHistory
                        Arguments.of(List.of(), List.of(), "", "", true), //shouldReplaceExistingQuotesIfExistingHistoryIsEmpty
                        Arguments.of(List.of(currentQuote), List.of(currentQuote, quoteYesterday), "", "", true),
                        //shouldReplaceExistingQuotesIfNewHistorySizeEqualOrGreater
                        Arguments.of(List.of(currentQuote, quoteYesterday), List.of(currentQuote), "", "Existing history had more entries.*", true),
                        //shouldReplaceExistingQuotesIfExistingHistorySizeEqualOrGreater
                        Arguments.of(List.of(currentQuote), List.of(oldQuote), "", "Existing history had newer results.*", true),
                        //shouldReplaceExistingIfExistingHistoryIsNewer
                        Arguments.of(List.of(oldQuote), List.of(currentQuote), "", "", true) //shouldReplaceExistingIfNewHistoryIsNewer
        );
    }

    private JPAQuote createQuote(JPAQuoteIdentifier quoteIdentifier) {
        JPAQuote quote = new JPAQuote();
        quote.setQuoteAmount(1.1);
        quote.setQuoteDate(LocalDate.now());
        quote.setQuoteCurrency("EUR");
        quote.setQuoteType(QuoteType.PRICE);
        quote.setVersion(1);
        quote.setIdentifier(quoteIdentifier);
        return quote;
    }

    private IsinQuoteHistory createQuoteHistory(String isin, int... days) {
        IsinQuoteHistory quoteHistory = new IsinQuoteHistory(isin, "n/a", QuoteSource.REFINITIV_RKD);
        for (int daysPast : days) {
            quoteHistory.addQuote(LocalDate.now().minusDays(daysPast), "EUR", 1.4);
        }
        return quoteHistory;
    }

    private void mockTransactionTemplate() {
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
    }
}
