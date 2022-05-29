package com.wealthpilot.quote.store;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.DynamicUpdate;
import com.wealthpilot.quote.store.util.BaseEntity;
import com.wealthpilot.quote.store.util.Quote;
import com.wealthpilot.quote.store.util.QuoteType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@Entity(name = "Quote") // set custom name to avoid conflict with com.wealthpilot.transactions.infrastructure.persistence.jpa.JPAQuote
@Table(name = "quote")
@DynamicUpdate
@ToString
public class JPAQuote extends BaseEntity {
    @Version
    private int version;

    @Column(name = "quote_date")
    private LocalDate quoteDate;
    @Column(name = "quote_amount")
    private Double quoteAmount;
    @Column(name = "quote_currency")
    private String quoteCurrency;
    @Column(name = "quote_type")
    @Enumerated(EnumType.STRING)
    private QuoteType quoteType;

    @ManyToOne
    @JoinColumn(name = "quote_identifier_id")
    private JPAQuoteIdentifier identifier;

    Quote toQuote() {
        return new Quote(getQuoteCurrency(), getQuoteAmount(), getQuoteDate());
    }
}
