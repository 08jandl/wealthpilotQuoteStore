package com.wealthpilot.quote.store.util;

/**
 * Defines a (persistent) store for quotes.
 * This is a write-only interface. The store will typically provide other interface like {@link QuoteService} for read-access to the stored data.
 */
public interface QuoteStore {
    void storeQuote(IsinQuote quote);
    IsinQuoteHistory getQuoteHistory(String isin);
}
