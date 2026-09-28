package com.pricealert.domain.coupon;
import java.math.BigDecimal;
import java.time.Instant;
public record Coupon(String code, BigDecimal discountPercentage, BigDecimal discountValue,
                     BigDecimal minimumPurchase, Instant expiresAt, String source) {}

