package com.pricealert.service;
import com.pricealert.TestSupport;
import com.pricealert.domain.product.*;
import com.pricealert.domain.coupon.Coupon;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DiscountServiceTest {
    @Test void discoveryRequiresAtLeastTwentyFivePercentIncludingHistoricalLows() {
        var config=org.mockito.Mockito.mock(com.pricealert.config.MonitorConfig.class);
        org.mockito.Mockito.when(config.minimumSamples()).thenReturn(3);
        org.mockito.Mockito.when(config.historyMinimumAge()).thenReturn(Duration.ofDays(7));
        org.mockito.Mockito.when(config.minStoreDiscount()).thenReturn(new BigDecimal("25"));
        org.mockito.Mockito.when(config.minHistoricalDiscount()).thenReturn(new BigDecimal("25"));
        var discounts=new DiscountService(config);
        assertThat(discounts.analyze(null,TestSupport.snapshot("75","100"),List.of(),null,false).shouldAlert()).isTrue();
        assertThat(discounts.analyze(null,TestSupport.snapshot("75.01","100"),List.of(),null,false).shouldAlert()).isFalse();
        assertThat(discounts.analyze(null,TestSupport.snapshot("99",null),TestSupport.history("100","100","100"),null,false).shouldAlert()).isFalse();
        assertThat(discounts.analyze(null,TestSupport.snapshot("75",null),TestSupport.history("100","100","100"),null,false).shouldAlert()).isTrue();
    }
    private final DiscountService service=new DiscountService(TestSupport.config());
    @Test void calculatesExactDecimalDiscount() {
        assertThat(DiscountService.discount(new BigDecimal("199.90"),new BigDecimal("119.94"))).isEqualByComparingTo("40");
    }
    @Test void initialStoreDealIsUnverifiedAndNotHistoricalLow() {
        var result=service.analyze(null,TestSupport.snapshot("60","100"),List.of(),null,false);
        assertThat(result.shouldAlert()).isTrue(); assertThat(result.newHistoricalLow()).isFalse();
        assertThat(result.reason()).isEqualTo("STORE_DISCOUNT_UNVERIFIED");
    }
    @Test void rejectsInflatedReferenceWhenHistoryExists() {
        Product old=new Product(); old.currentPrice=new BigDecimal("100");
        var result=service.analyze(old,TestSupport.snapshot("100","1000"),TestSupport.history("90","100","100"),null,false);
        assertThat(result.sufficientHistory()).isTrue(); assertThat(result.shouldAlert()).isFalse();
    }
    @Test void detectsHistoricalDiscountWithoutNewLow() {
        var result=service.analyze(null,TestSupport.snapshot("80",null),TestSupport.history("50","150","150"),null,false);
        assertThat(result.newHistoricalLow()).isFalse(); assertThat(result.shouldAlert()).isTrue();
        assertThat(result.historicalDiscount()).isEqualByComparingTo("31.43");
    }
    @Test void newHistoricalLowAndTrackedTarget() {
        var low=service.analyze(null,TestSupport.snapshot("90",null),TestSupport.history("100"),null,false);
        assertThat(low.newHistoricalLow()).isTrue(); assertThat(low.shouldAlert()).isFalse();
        var target=service.analyze(null,TestSupport.snapshot("90",null),List.of(),new BigDecimal("90"),true);
        assertThat(target.reason()).isEqualTo("TARGET_PRICE");
    }
    @Test void trackedDropUsesPreviousObservedPrice() {
        Product old=new Product(); old.currentPrice=new BigDecimal("100"); old.available=true;
        var result=service.analyze(old,TestSupport.snapshot("90",null),TestSupport.history("80","100"),null,true);
        assertThat(result.reason()).isEqualTo("PRICE_DROP");
    }
    @Test void unavailableNeverAlerts() {
        var s=TestSupport.snapshot("10","100");
        var unavailable=new ProductSnapshot(s.externalId(),s.name(),s.url(),null,s.currentPrice(),s.originalPrice(),s.store(),false,null,s.collectedAt());
        assertThat(service.analyze(null,unavailable,List.of(),null,false).shouldAlert()).isFalse();
    }
    @Test void couponRespectsExpiryMinimumAndDecimalMath() {
        var coupons=new CouponService(); Instant now=Instant.now();
        Coupon coupon=new Coupon("TEST",new BigDecimal("10"),null,new BigDecimal("50"),now.plusSeconds(60),"https://www.kabum.com.br");
        assertThat(coupons.effectivePrice(new BigDecimal("99.90"),coupon,now)).isEqualByComparingTo("89.91");
        assertThat(coupons.effectivePrice(new BigDecimal("49.90"),coupon,now)).isEqualByComparingTo("49.90");
        assertThat(coupons.effectivePrice(new BigDecimal("99.90"),coupon,now.plusSeconds(61))).isEqualByComparingTo("99.90");
        Coupon fixed=new Coupon("FIXED",null,new BigDecimal("20"),null,null,"source");
        assertThat(coupons.effectivePrice(new BigDecimal("15"),fixed,now)).isEqualByComparingTo("0");
    }
}

