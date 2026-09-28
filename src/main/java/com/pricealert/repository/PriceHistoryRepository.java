package com.pricealert.repository;
import com.pricealert.domain.price.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.List;
public interface PriceHistoryRepository extends JpaRepository<PriceHistory,Long> {
    List<PriceHistory> findByProductIdOrderByCollectedAtAsc(Long productId);
    Page<PriceHistory> findByProductId(Long productId, Pageable page);
}

