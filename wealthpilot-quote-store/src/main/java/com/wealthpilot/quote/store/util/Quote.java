package com.wealthpilot.quote.store.util;

import java.time.LocalDate;

import org.springframework.lang.Nullable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author christof.dallermassl
 * @since 2.4.0
 */
@Getter
@Setter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Quote {

    public static final int MAX_DAYS_VALID_QUOTE = 5;

    @Nullable
    private String currency;

    private final double amount;
    private final LocalDate date;

    public boolean isValidForDate(LocalDate dateToUseQuoteFor) {
        // quote must be max MAX_DAYS_VALID_QUOTE older than given date:
        return !date.plusDays(MAX_DAYS_VALID_QUOTE).isBefore(dateToUseQuoteFor);
    }
}
