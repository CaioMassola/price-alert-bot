package com.pricealert.service;

import com.pricealert.TestSupport;
import com.pricealert.domain.coupon.*;
import com.pricealert.domain.product.*;
import com.pricealert.domain.store.Store;
import com.pricealert.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    @Test void persistsCouponTermsAndUpdatesExistingCoupon() {
        var products=mock(ProductRepository.class); var history=mock(PriceHistoryRepository.class);
        var coupons=mock(CouponRepository.class); var alerts=mock(AlertService.class);
        var service=new ProductService(products,history,coupons,new DiscountService(TestSupport.config()),
            mock(PriceHistoryService.class),new CouponService(),alerts);
        Product product=new Product(); product.id=1L;
        when(products.findByStoreAndExternalId(Store.KABUM,"123")).thenReturn(Optional.of(product));
        Coupon coupon=new Coupon("SALE",BigDecimal.TEN,null,null,null,"https://www.kabum.com.br/produto/123");
        var item=new ProductSnapshot("123","Keyboard",coupon.source(),null,new BigDecimal("100"),new BigDecimal("200"),Store.KABUM,true,coupon,Instant.now());
        service.accept(item,null,false);
        var saved=org.mockito.ArgumentCaptor.forClass(CouponEntity.class); verify(coupons).save(saved.capture());
        assertThat(saved.getValue().discountPercentage).isEqualByComparingTo("10");
        when(coupons.findByProductIdAndCode(1L,"SALE")).thenReturn(Optional.of(saved.getValue()));
        service.accept(item,null,false); verify(coupons,times(2)).save(saved.getValue());
        assertThat(product.couponCode).isEqualTo("SALE");
    }
    @Test void rejectsInvalidSnapshotsBeforePersistence() {
        assertThatThrownBy(()->new ProductSnapshot("123","Keyboard","https://www.kabum.com.br/produto/123",null,
            BigDecimal.ZERO,null,Store.KABUM,true,null,Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ProductSnapshot("123","x".repeat(1001),"https://www.kabum.com.br/produto/123",null,
            BigDecimal.ONE,null,Store.KABUM,true,null,Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
