package com.pricealert.domain.product;
import java.math.BigDecimal;
import java.time.Instant;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.coupon.Coupon;
public record ProductSnapshot(String externalId, String name, String url, String imageUrl,
    BigDecimal currentPrice, BigDecimal originalPrice, Store store, boolean available,
    Coupon coupon, Instant collectedAt) {
    public ProductSnapshot {
        if (externalId == null || externalId.isBlank() || name == null || name.isBlank() ||
            currentPrice == null || currentPrice.signum() <= 0 || store == null || collectedAt == null)
            throw new IllegalArgumentException("Resposta da loja sem identificacao ou preco valido");
        if (externalId.length() > 200 || name.length() > 1000 || url == null || url.length() > 2048)
            throw new IllegalArgumentException("Resposta da loja excede os limites");
        store.validateUrl(url);
        if (imageUrl != null && (!imageUrl.startsWith("https://") || imageUrl.length() > 2048)) imageUrl = null;
        if (originalPrice != null && originalPrice.signum() <= 0) originalPrice = null;
        currentPrice = currentPrice.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

