package com.pricealert.repository;
import com.pricealert.domain.product.TrackedProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TrackedProductRepository extends JpaRepository<TrackedProduct,Long> {
    List<TrackedProduct> findByActiveTrueOrderByIdAsc();
}

