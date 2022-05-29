package com.wealthpilot.quote.store;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ff4j.FF4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import com.wealthpilot.quote.store.util.Constants;
import com.wealthpilot.quote.store.util.IsinQuote;
import com.wealthpilot.quote.store.util.IsinQuoteHistory;
import com.wealthpilot.quote.store.util.Quote;
import com.wealthpilot.quote.store.util.QuoteSource;
import com.wealthpilot.quote.store.util.QuoteStore;
import com.wealthpilot.quote.store.util.QuoteType;

import io.micrometer.core.annotation.Timed;
import lombok.extern.log4j.Log4j2;

@Transactional
@Service
@Log4j2
public class JPAQuoteService implements QuoteStore {

    private final JPAQuoteRepository quoteRepository;
    private final JPAQuoteIdentifierRepository quoteIdentifierRepository;
    private final TransactionTemplate transactionTemplate;
    private final FF4j ff4j;

    @java.beans.ConstructorProperties({ "quoteRepository", "quoteIdentifierRepository", "transactionTemplate", "ff4j" })
    public JPAQuoteService(JPAQuoteRepository quoteRepository, JPAQuoteIdentifierRepository quoteIdentifierRepository, TransactionTemplate transactionTemplate,
                    FF4j ff4j) {
        this.quoteRepository = quoteRepository;
        this.quoteIdentifierRepository = quoteIdentifierRepository;
        this.transactionTemplate = transactionTemplate;
        this.ff4j = ff4j;
    }

    @Override
    public IsinQuoteHistory getQuoteHistory(final String isin) {
        JPAQuoteIdentifier quoteIdentifier = quoteIdentifierRepository.findOneByIsin(isin).orElseGet(() -> createIdentifierAndFetch(isin));
        return getIsinQuoteHistory(quoteIdentifier);
    }

    private synchronized JPAQuoteIdentifier createIdentifierAndFetch(final String isin) {
        return quoteIdentifierRepository.findOneByIsin(isin).orElseGet(() -> Objects.requireNonNull(transactionTemplate.execute(status -> getOrCreateQuoteIdentifier(isin))));
    }

    private IsinQuoteHistory getIsinQuoteHistory(final JPAQuoteIdentifier quoteIdentifier) {
        List<JPAQuote> quotes = quoteRepository.findAllByIdentifier(quoteIdentifier);
        return toIsinQuoteHistory(quoteIdentifier, quotes);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeQuote(IsinQuote quoteFromBank) {
        JPAQuoteIdentifier quoteIdentifier = getOrCreateQuoteIdentifier(quoteFromBank.getIsin());
        final Optional<JPAQuote> dbQuoteForDate = quoteRepository.findByIdentifierAndQuoteDate(quoteIdentifier, quoteFromBank.getQuote().getDate());
        if (dbQuoteForDate.isPresent()) {
            log.debug("Quote is already saved {}", quoteFromBank);
            return;
        }

            final Optional<JPAQuote> currentQuote = quoteRepository.findFirstByIdentifierOrderByQuoteDateDesc(quoteIdentifier);
            if (currentQuote.isEmpty()) {
                updateIdentifier(quoteIdentifier, quoteFromBank.getQuoteSource(), quoteFromBank.getMarketPlace());
                quoteRepository.save(toQuoteStoreQuote(quoteIdentifier, quoteFromBank.getQuote()));
            } else {
                if (!currentQuote.get().toQuote().isValidForDate(LocalDate.now())) {
                    log.warn("No valid quote for {}", quoteIdentifier);
                } else {
                    log.debug("Valid quotes already present, ignoring quote from bank {}", quoteFromBank);
                }
            }
    }

    @Timed
    @Scheduled(cron = "${quote-store.update.cron}")
    public void updateAllQuotes() {
        if (!ff4j.check("BATCH_UPDATE_QUOTE_STORE")) {
            return;
        }
    }

    private Optional<LocalDate> getLatestQuoteDate(final List<JPAQuote> quotes) {
        return quotes.stream().filter(quote -> Objects.nonNull(quote.getQuoteAmount())).map(JPAQuote::getQuoteDate).max(Comparator.naturalOrder());
    }

    protected boolean shouldReplaceExistingQuotes(final JPAQuoteIdentifier identifier, final List<JPAQuote> existingQuotes, final List<JPAQuote> newQuotes) {
        Optional<LocalDate> latestDateExisting = getLatestQuoteDate(existingQuotes);
        if (latestDateExisting.isEmpty()) {
            log.debug("Quote store has empty result: {}", existingQuotes);
            return true;
        }
        Optional<LocalDate> latestDateNew = getLatestQuoteDate(newQuotes);
        if (latestDateNew.isEmpty()) {
            log.info("Not replacing existing quotes with empty history for {}: {} entries until {}", identifier, existingQuotes.size(), latestDateExisting);
            return false;
        }
        if (latestDateExisting.get().isEqual(latestDateNew.get())) {
            if (existingQuotes.size() > newQuotes.size()) {
                log.warn("Existing history had more entries: replacing {} entries with {}, latest entry {}", existingQuotes.size(), newQuotes.size(),
                                latestDateNew.get());
            } else {
                log.debug("New history has equal or more entries: replacing {} entries with {}, latest entry {}", existingQuotes.size(), newQuotes.size(),
                                latestDateNew.get());
            }
        } else {
            if (latestDateNew.get().isBefore(latestDateExisting.get())) {
                log.warn("Existing history had newer results, latest entry existing {}, new {} ", latestDateExisting.get(), latestDateNew.get());
            } else {
                log.debug("New history had newer results, latest entry existing {}, new {} ", latestDateExisting.get(), latestDateNew.get());
            }
        }
        return true;
    }

    private IsinQuoteHistory toIsinQuoteHistory(final JPAQuoteIdentifier identifier, final List<JPAQuote> quotes) {
        String marketPlace = identifier.getMarketPlace();
        QuoteSource quoteSource = identifier.getQuoteSource();
        IsinQuoteHistory history = new IsinQuoteHistory(identifier.getIsin(), marketPlace, quoteSource);
        for (JPAQuote quote: quotes) {
            final Quote quoteInCorrectFormat = quote.toQuote();
            history.addQuote(quoteInCorrectFormat);
        }
        return history;
    }

    private JPAQuote toQuoteStoreQuote(final JPAQuoteIdentifier identifier, final Quote externalQuote) {
        String currency = getQuoteCurrency(externalQuote);
        JPAQuote quote = new JPAQuote();
        quote.setIdentifier(identifier);
        quote.setQuoteAmount(externalQuote.getAmount());
        quote.setQuoteCurrency(currency);
        quote.setQuoteDate(externalQuote.getDate());
        quote.setQuoteType("%".equals(currency) ? QuoteType.PERCENTAGE : QuoteType.PRICE);
        return quote;
    }

    private String getQuoteCurrency(final Quote quote) {
        String currency = quote.getCurrency();
        if (!StringUtils.hasText(currency)) {
            log.warn("Quote does not have currency, returning default '{}' for {}", Constants.DEFAULT_CURRENCY, quote);
            return Constants.DEFAULT_CURRENCY;
        }
        if (!"%".equals(currency) && currency.length() != 3) {
            log.warn("Unexpected currency {} for {}", currency, quote);
        }
        return currency.toUpperCase();
    }

    private JPAQuoteIdentifier getOrCreateQuoteIdentifier(final String isin) {
        return quoteIdentifierRepository.findAndLockOneByIsin(isin).orElseGet(() -> createIdentifierInTransaction(isin));
    }

    private JPAQuoteIdentifier createIdentifierInTransaction(final String isin) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> createIdentifier(isin)));
        } catch (DataIntegrityViolationException e) {
            log.info("Conflict on concurrent insert of quote-identifier: {}", isin);
            return quoteIdentifierRepository.findAndLockOneByIsin(isin).orElseThrow();
        }
    }

    protected JPAQuoteIdentifier createIdentifier(String isin) {
        JPAQuoteIdentifier identifier = new JPAQuoteIdentifier();
        identifier.setIsin(isin);
        identifier.setFetchDate(ZonedDateTime.now().minusYears(1));
        identifier.setMarketPlace("n/a");
        identifier.setQuoteSource(QuoteSource.MANUAL);
        return quoteIdentifierRepository.save(identifier);
    }

    private void updateIdentifier(final JPAQuoteIdentifier identifier, QuoteSource quoteSource, @Nullable String marketPlace) {
        String newMarketPlace = Objects.requireNonNullElse(marketPlace, "n/a");
        if (!Objects.equals(newMarketPlace, identifier.getMarketPlace())) {
            log.info("Market place has changed from {} to {} for {}", identifier.getMarketPlace(), newMarketPlace, identifier);
        }
        identifier.setFetchDate(ZonedDateTime.now());
        identifier.setMarketPlace(newMarketPlace);
        identifier.setQuoteSource(quoteSource);
        quoteIdentifierRepository.save(identifier);
    }
}
