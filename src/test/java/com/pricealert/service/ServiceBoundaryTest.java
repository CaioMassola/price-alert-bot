package com.pricealert.service;
import com.pricealert.TestSupport;
import com.pricealert.domain.product.*;
import com.pricealert.domain.price.PriceHistory;
import com.pricealert.domain.coupon.Coupon;
import com.pricealert.domain.alert.*;
import com.pricealert.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ServiceBoundaryTest {
    private final DiscountService discounts=new DiscountService(TestSupport.config());
    private ProductSnapshot snapshot(boolean available,Coupon coupon) {
        var s=TestSupport.snapshot("100",null);
        return new ProductSnapshot(s.externalId(),s.name(),s.url(),null,s.currentPrice(),null,s.store(),available,coupon,s.collectedAt());
    }
    @Test void couponIgnoresInvalidAmountsAndAllowsAmountOnlyDiscounts() {
        var service=new CouponService();
        for(BigDecimal percent:Arrays.asList(null,BigDecimal.ZERO,new BigDecimal("101")))
            for(BigDecimal value:Arrays.asList(null,BigDecimal.ZERO,BigDecimal.TEN)) {
                var coupon=new Coupon("SALE",percent,value,null,null,"source");
                assertThat(service.effectivePrice(new BigDecimal("100"),coupon,Instant.EPOCH))
                    .isEqualByComparingTo(value!=null && value.signum()>0?"90":"100");
            }
        assertThat(DiscountService.discount(BigDecimal.ZERO,BigDecimal.TEN)).isEqualByComparingTo("0");
    }
    @Test void trackedRestocksAndCouponsOnlyAlertOnTransitions() {
        var previous=new Product(); previous.currentPrice=new BigDecimal("100"); previous.available=false;
        assertThat(discounts.analyze(previous,snapshot(false,null),List.of(),null,true).shouldAlert()).isFalse();
        assertThat(discounts.analyze(previous,snapshot(true,null),List.of(),null,true).reason()).isEqualTo("BACK_IN_STOCK");
        previous.available=true;
        var coupon=new Coupon("NEW",null,null,null,null,"source");
        assertThat(discounts.analyze(previous,snapshot(true,coupon),List.of(),null,true).reason()).isEqualTo("NEW_COUPON");
        previous.couponCode="NEW";
        assertThat(discounts.analyze(previous,snapshot(true,coupon),List.of(),null,true).shouldAlert()).isFalse();
        assertThat(discounts.analyze(null,snapshot(true,coupon),List.of(),null,true).reason()).isEqualTo("NEW_COUPON");
        assertThat(discounts.analyze(previous,snapshot(false,null),List.of(),null,true).shouldAlert()).isFalse();
        assertThat(discounts.analyze(previous,snapshot(true,null),List.of(),new BigDecimal("99"),true).shouldAlert()).isFalse();
        var history=TestSupport.history("100","100","100");
        history.forEach(h->h.collectedAt=Instant.now());
        assertThat(discounts.analyze(previous,snapshot(true,null),history,null,false).sufficientHistory()).isFalse();
        history.forEach(h->h.collectedAt=Instant.now().minus(Duration.ofDays(31)));
        assertThat(discounts.analyze(previous,snapshot(true,null),history,null,false).sufficientHistory()).isFalse();
        assertThat(discounts.analyze(previous,TestSupport.snapshot("60","100"),List.of(),null,true).shouldAlert()).isTrue();
    }
    @Test void historyRecordsReferenceCouponAndStockChangesWithoutDuplicateObservations() {
        for(String before:Arrays.asList(null,"120")) for(String after:Arrays.asList(null,"120","130")) {
            var repository=mock(PriceHistoryRepository.class); var service=new PriceHistoryService(repository);
            var product=new Product(); product.id=1L;
            var rows=new ArrayList<PriceHistory>();
            var first=TestSupport.snapshot("100",before);
            service.record(product,first,discounts.analyze(null,first,List.of(),null,false),rows);
            var next=TestSupport.snapshot("100",after);
            service.record(product,next,discounts.analyze(null,next,List.of(),null,false),rows);
            assertThat(rows).hasSize(Objects.equals(before,after)?1:2);
        }
        var repository=mock(PriceHistoryRepository.class); var service=new PriceHistoryService(repository);
        var product=new Product(); product.id=1L; var rows=new ArrayList<PriceHistory>();
        for(var s:List.of(snapshot(true,null),snapshot(true,new Coupon("SALE",null,null,null,null,"source")),snapshot(false,new Coupon("SALE",null,null,null,null,"source"))))
            service.record(product,s,discounts.analyze(null,s,List.of(),null,false),rows);
        assertThat(rows).hasSize(3);
        rows.getFirst().collectedAt=Instant.now().minus(Duration.ofDays(31)); rows.getFirst().price=BigDecimal.ONE;
        var next=TestSupport.snapshot("110",null);
        service.record(product,next,discounts.analyze(null,next,List.of(),null,false),rows);
        assertThat(product.lowestPrice).isEqualByComparingTo("1");
        assertThat(product.average30DayPrice).isGreaterThan(product.averagePrice);
    }
    private PriceAlert alert(String reason,boolean low,boolean sufficient,boolean send,Coupon coupon) {
        var s=snapshot(true,coupon);
        return new PriceAlert(1L,s,new DiscountAnalysis(s.currentPrice(),null,null,null,BigDecimal.TEN,new BigDecimal("20"),BigDecimal.ZERO,sufficient,low,send,reason),s.currentPrice());
    }
    @Test void serializationFailureDoesNotEnqueueIncompletePayload() throws Exception {
        var repository=mock(AlertRepository.class); var mapper=mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialization") {});
        var service=new AlertService(repository,TestSupport.config(),mapper);
        var product=new Product(); product.id=1L;
        assertThatThrownBy(()->service.enqueue(product,alert("TARGET_PRICE",false,false,true,null))).isInstanceOf(IllegalStateException.class);
        verify(repository,never()).save(any());
    }
    @Test void cooldownExceptionsDistinguishRestockTargetAndRepeatedCoupons() {
        var repository=mock(AlertRepository.class);
        var service=new AlertService(repository,TestSupport.config(),new ObjectMapper().findAndRegisterModules());
        var previous=new Alert(); previous.createdAt=Instant.now(); previous.price=new BigDecimal("100"); previous.couponCode="SALE";
        assertThat(service.canSend(previous,alert("NONE",true,false,true,null),null,Instant.now())).isTrue();
        assertThat(service.canSend(previous,alert("NONE",false,false,true,null),"SALE",Instant.now())).isFalse();
        assertThat(service.canSend(previous,alert("BACK_IN_STOCK",false,false,true,null),null,Instant.now())).isTrue();
        assertThat(service.canSend(previous,alert("TARGET_PRICE",false,false,true,null),null,Instant.now())).isTrue();
        previous.alertType="TARGET_PRICE";
        assertThat(service.canSend(previous,alert("TARGET_PRICE",false,false,true,null),null,Instant.now())).isFalse();
        var product=new Product(); product.id=1L;
        service.enqueue(product,alert("NONE",false,false,false,null)); verifyNoInteractions(repository);
        when(repository.findFirstByProductIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(previous));
        service.enqueue(product,alert("NONE",false,false,true,null)); verify(repository,never()).save(any());
        service.enqueue(product,alert("BACK_IN_STOCK",false,true,true,new Coupon("NEW",null,null,null,null,"source")));
        verify(repository).save(argThat(a->a.discount.compareTo(new BigDecimal("20"))==0 && "NEW".equals(a.couponCode)));
    }
}
