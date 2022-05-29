package com.wealthpilot.quote.store.util;

public enum QuoteSource {
    BANK_API(false), NDGIT(true), REFINITIV_RKD(true), REFINITIV_RDP(true), MANUAL(false);

    private final boolean backwardsCorrectsSplits;

    QuoteSource(boolean correctsSplits) {
        this.backwardsCorrectsSplits = correctsSplits;
    }

    public boolean isBackwardsCorrectingSplits() {
        return backwardsCorrectsSplits;
    }
}
