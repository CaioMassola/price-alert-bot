package com.pricealert.domain.product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.pricealert.domain.store.Store;
@Entity @Table(name="tracked_products",uniqueConstraints=@UniqueConstraint(columnNames={"store","url"}))
public class TrackedProduct {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Store store;
    @Column(nullable=false,length=2048) public String url;
    public BigDecimal targetPrice;
    public boolean active=true;
    public Instant createdAt=Instant.now();
}

