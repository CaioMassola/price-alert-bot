package com.pricealert.domain.alert;
import java.math.BigDecimal;
public record DiscountAnalysis(BigDecimal currentPrice, BigDecimal previousPrice, BigDecimal historicalAverage,
    BigDecimal historicalLowest, BigDecimal storeDiscount, BigDecimal historicalDiscount,
    BigDecimal priceChangePercentage, boolean sufficientHistory, boolean newHistoricalLow,
    boolean shouldAlert, String reason) {}

