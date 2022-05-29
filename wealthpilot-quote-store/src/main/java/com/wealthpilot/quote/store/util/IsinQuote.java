package com.wealthpilot.quote.store.util;

import java.time.LocalDate;

import org.springframework.lang.Nullable;

import lombok.Data;

@Data
public class IsinQuote {

    @Nullable
    private final String marketPlace;
    private final String isin;
    private final LocalDate fetchDate;
    private final Quote quote;
    private final QuoteSource quoteSource;
    private final boolean nominalType;
}
