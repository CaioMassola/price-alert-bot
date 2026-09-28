package com.pricealert.domain.alert;
import com.pricealert.domain.product.ProductSnapshot;
import java.math.BigDecimal;
public record PriceAlert(Long productId, ProductSnapshot product, DiscountAnalysis analysis, BigDecimal effectivePrice) {}

