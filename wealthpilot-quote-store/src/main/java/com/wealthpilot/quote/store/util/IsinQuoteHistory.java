package com.wealthpilot.quote.store.util;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.lang.Nullable;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * @author christof.dallermassl
 * @since 2.4.0
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class IsinQuoteHistory {

    public static final IsinQuoteHistory EMPTY_QUOTE_HISTORY = new IsinQuoteHistory("n/a", LocalDate.now(), "n/a", QuoteSource.MANUAL, Map.of());

    private final String isin;
    private final LocalDate fetchDate;
    @Nullable
    private final String marketPlace;
    private final QuoteSource quoteSource;
    private final Map<LocalDate, Quote> quotes;

    public IsinQuoteHistory(String isin, @Nullable String marketPlace, QuoteSource quoteSource) {
        this(isin, LocalDate.now(), marketPlace, quoteSource, new HashMap<>());
    }

    public String getIsin() {
        return isin;
    }

    public LocalDate getFetchDate() {
        return fetchDate;
    }

    @Nullable
    public String getMarketPlace() {
        return marketPlace;
    }

    public QuoteSource getQuoteSource() {
        return quoteSource;
    }

    public Map<LocalDate, Quote> getQuotes() {
        return Collections.unmodifiableMap(quotes);
    }

    public Optional<Quote> getQuote(LocalDate date) {
        return Optional.ofNullable(quotes.get(date));
    }

    public void addQuote(Quote quote) {
        addQuote(quote.getDate(), quote);
    }

    public void addQuote(LocalDate date, Quote quote) {
        quotes.put(date, quote);
    }

    public void addQuote(LocalDate date, String currency, double quote) {
        addQuote(date, new Quote(currency, quote, date));
    }

    public Optional<LocalDate> getLatestDate() {
        return quotes.keySet().stream().max(LocalDate::compareTo);
    }

    public Optional<LocalDate> getFirstDate() {
        return quotes.keySet().stream().min(LocalDate::compareTo);
    }

    @Override
    public String toString() {
        return "IsinQuoteHistory [isin=" + isin + ", marketPlace=" + marketPlace + ", " + quotes.size() + " quotes, firstDate=" + getFirstDate()
            + ", latestDate=" + getLatestDate() + "]";
    }
}
