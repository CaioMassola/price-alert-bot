package com.pricealert.service;
import com.pricealert.domain.coupon.Coupon;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.Instant;
@Service
public class CouponService {
    public BigDecimal effectivePrice(BigDecimal listed, Coupon coupon, Instant now) {
        if(coupon==null || coupon.expiresAt()!=null && !coupon.expiresAt().isAfter(now) ||
            coupon.minimumPurchase()!=null && listed.compareTo(coupon.minimumPurchase())<0) return listed;
        BigDecimal result=listed;
        if(coupon.discountPercentage()!=null && coupon.discountPercentage().signum()>0 && coupon.discountPercentage().compareTo(BigDecimal.valueOf(100))<=0)
            result=listed.multiply(BigDecimal.ONE.subtract(coupon.discountPercentage().movePointLeft(2)));
        else if(coupon.discountValue()!=null && coupon.discountValue().signum()>0) result=listed.subtract(coupon.discountValue());
        return result.max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
    }
}

