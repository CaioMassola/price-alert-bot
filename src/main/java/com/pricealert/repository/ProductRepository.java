package com.pricealert.repository;
import com.pricealert.domain.product.Product;
import com.pricealert.domain.store.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface ProductRepository extends JpaRepository<Product,Long> {
    Optional<Product> findByStoreAndExternalId(Store store, String externalId);
    Page<Product> findByDealTrueAndAvailableTrueAndLastCheckedAtAfter(java.time.Instant cutoff, Pageable page);
}

