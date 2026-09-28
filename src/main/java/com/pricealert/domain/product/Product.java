package com.pricealert.domain.product;
import jakarta.persistence.*;
import com.pricealert.domain.store.Store;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="products", uniqueConstraints=@UniqueConstraint(columnNames={"store","external_id"}))
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Store store;
    @Column(name="external_id",nullable=false,length=200) public String externalId;
    @Column(nullable=false,length=1000) public String name;
    @Column(nullable=false,length=2048) public String url;
    @Column(length=2048) public String imageUrl;
    public BigDecimal currentPrice;
    public BigDecimal previousPrice;
    public BigDecimal originalPrice;
    public BigDecimal lowestPrice;
    public BigDecimal highestPrice;
    public BigDecimal averagePrice;
    public BigDecimal average30DayPrice;
    public BigDecimal lastAlertPrice;
    public BigDecimal storeDiscount;
    public BigDecimal historicalDiscount;
    public boolean active=true;
    public boolean available;
    public boolean deal;
    @Column(length=200) public String couponCode;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant lastCheckedAt;
    @Version public Long version;
}

