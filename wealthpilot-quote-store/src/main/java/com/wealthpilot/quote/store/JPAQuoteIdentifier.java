package com.wealthpilot.quote.store;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.DynamicUpdate;
import com.wealthpilot.quote.store.util.BaseEntity;
import com.wealthpilot.quote.store.util.QuoteSource;

@Entity
@Table(name = "quote_identifier")
@DynamicUpdate
public class JPAQuoteIdentifier extends BaseEntity {
    @Version
    private int version;
    @Column(name = "isin")
    private String isin;
    @Column(name = "fetch_date")
    private ZonedDateTime fetchDate;
    @Column(name = "market_place")
    private String marketPlace;
    @Enumerated(EnumType.STRING)
    @Column(name = "quote_source")
    private QuoteSource quoteSource;

    public int getVersion() {
        return this.version;
    }

    public String getIsin() {
        return this.isin;
    }

    public ZonedDateTime getFetchDate() {
        return this.fetchDate;
    }

    public String getMarketPlace() {
        return this.marketPlace;
    }

    public QuoteSource getQuoteSource() {
        return this.quoteSource;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public void setFetchDate(ZonedDateTime fetchDate) {
        this.fetchDate = fetchDate;
    }

    public void setMarketPlace(String marketPlace) {
        this.marketPlace = marketPlace;
    }

    public void setQuoteSource(QuoteSource quoteSource) {
        this.quoteSource = quoteSource;
    }

    public String toString() {
        return "JPAQuoteIdentifier(version=" + this.getVersion() + ", isin=" + this.getIsin() + ", fetchDate=" + this.getFetchDate() + ", marketPlace="
                        + this.getMarketPlace() + ", quoteSource=" + this.getQuoteSource() + ")";
    }
}