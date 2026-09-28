package com.pricealert.domain.price;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="price_history")
public class PriceHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false) public BigDecimal price;
    public BigDecimal originalPrice;
    public BigDecimal discountPercentage;
    @Column(length=200) public String coupon;
    public boolean available;
    @Column(nullable=false) public Instant collectedAt;
}

