package com.pricealert.domain.coupon;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="coupons",uniqueConstraints=@UniqueConstraint(columnNames={"product_id","code"}))
public class CouponEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false,length=200) public String code;
    public BigDecimal discountPercentage;
    public BigDecimal discountValue;
    public BigDecimal minimumPurchase;
    public Instant expiresAt;
    @Column(length=2048) public String source;
}

