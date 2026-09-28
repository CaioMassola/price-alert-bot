package com.pricealert.domain.alert;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="alerts")
public class Alert {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false,length=100) public String alertType;
    public BigDecimal price;
    public BigDecimal discount;
    @Column(length=200) public String couponCode;
    @Column(nullable=false,length=64,unique=true) public String fingerprint;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false,columnDefinition="text") public String payload;
    public Instant createdAt;
    public Instant sentAt;
    public Instant nextAttemptAt;
    public int attempts;
    @Column(length=100) public String deliveryError;
}

