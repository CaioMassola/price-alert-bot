package com.pricealert.repository;
import com.pricealert.domain.coupon.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CouponRepository extends JpaRepository<CouponEntity,Long> {
    Optional<CouponEntity> findByProductIdAndCode(Long id, String code);
}

