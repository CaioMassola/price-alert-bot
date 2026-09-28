package com.pricealert.service;
import com.pricealert.TestSupport;
import com.pricealert.domain.alert.*;
import com.pricealert.domain.product.*;
import com.pricealert.repository.AlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AlertServiceTest {
    private final AlertRepository repository=mock(AlertRepository.class);
    private final AlertService service=new AlertService(repository,TestSupport.config(),new ObjectMapper().findAndRegisterModules());
    private PriceAlert deal(String price) {
        var snapshot=TestSupport.snapshot(price,"200");
        var analysis=new DiscountService(TestSupport.config()).analyze(null,snapshot,List.of(),null,false);
        return new PriceAlert(1L,snapshot,analysis,snapshot.currentPrice());
    }
    @Test void sameFingerprintIsNeverQueuedAgain() {
        when(repository.existsByFingerprint(anyString())).thenReturn(true);
        Product p=new Product(); p.id=1L; service.enqueue(p,deal("100"));
        verify(repository,never()).save(any());
    }
    @Test void cooldownBlocksEquivalentDealsButAllowsAdditionalDrop() {
        Alert previous=new Alert(); previous.createdAt=Instant.now(); previous.price=new BigDecimal("100");
        previous.alertType="STORE_DISCOUNT_UNVERIFIED";
        assertThat(service.canSend(previous,deal("99"),null,Instant.now())).isFalse();
        assertThat(service.canSend(previous,deal("89"),null,Instant.now())).isTrue();
        assertThat(service.canSend(previous,deal("99"),"NEW",Instant.now())).isTrue();
        assertThat(service.canSend(previous,deal("99"),null,Instant.now().plus(Duration.ofHours(7)))).isTrue();
    }
    @Test void persistsPayloadBeforeSending() {
        Product p=new Product(); p.id=1L;
        service.enqueue(p,deal("100"));
        verify(repository).save(argThat(a->a.status.equals("PENDING") && a.sentAt==null && a.payload.contains("Produto teste")));
    }
}

