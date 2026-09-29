package com.pricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricealert.TestSupport;
import com.pricealert.domain.alert.*;
import com.pricealert.domain.product.Product;
import com.pricealert.integration.*;
import com.pricealert.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertDeliveryServiceTest {
    final AlertRepository alerts=mock(AlertRepository.class);
    final ProductRepository products=mock(ProductRepository.class);
    final NotificationChannel channel=mock(NotificationChannel.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final AlertDeliveryService service=new AlertDeliveryService(alerts,products,channel,mapper,manager);
    final Product product=new Product();

    Alert pending() throws Exception {
        var snapshot=TestSupport.snapshot("100","200");
        var analysis=new DiscountService(TestSupport.config()).analyze(null,snapshot,List.of(),null,false);
        Alert alert=new Alert(); alert.productId=1L; alert.price=snapshot.currentPrice();
        alert.createdAt=Instant.now(); alert.status="PENDING";
        alert.payload=mapper.writeValueAsString(new PriceAlert(1L,snapshot,analysis,snapshot.currentPrice()));
        product.currentPrice=alert.price; product.available=true;
        when(channel.configured()).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(alerts.findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(eq("PENDING"),any()))
            .thenReturn(List.of(alert));
        return alert;
    }

    @Test void confirmsDeliveryAndIntroducesOnlyFirstBatch() throws Exception {
        Alert first=pending(); service.deliver();
        assertThat(first.status).isEqualTo("SENT");
        assertThat(first.sentAt).isNotNull(); assertThat(first.attempts).isEqualTo(1);
        assertThat(product.lastAlertPrice).isEqualByComparingTo("100");
        verify(channel).send(any(),eq(true));
        pending(); service.deliver(); verify(channel).send(any(),eq(false));
    }
    @Test void skipsUnavailableAndExpiredOffers() throws Exception {
        Alert alert=pending(); product.available=false; service.deliver();
        assertThat(alert.status).isEqualTo("STALE");
        alert=pending(); alert.createdAt=Instant.now().minusSeconds(3601); service.deliver();
        assertThat(alert.status).isEqualTo("STALE"); verify(channel,never()).send(any(),anyBoolean());
    }
    @Test void waitsAfterRateLimitWithoutRetryingImmediately() throws Exception {
        Alert alert=pending(); Instant retry=Instant.now().plusSeconds(60);
        doThrow(new NotificationException("RATE_LIMITED",retry)).when(channel).send(any(),anyBoolean());
        service.deliver(); service.deliver();
        assertThat(alert.status).isEqualTo("PENDING"); assertThat(alert.nextAttemptAt).isEqualTo(retry);
        verify(channel,times(1)).send(any(),anyBoolean());
    }
    @Test void preservesFailedDeliveryForReview() throws Exception {
        Alert alert=pending();
        doThrow(new NotificationException("FAILED",null)).when(channel).send(any(),anyBoolean());
        service.deliver(); assertThat(alert.status).isEqualTo("FAILED");
        assertThat(alert.deliveryError).isEqualTo("FAILED"); assertThat(alert.sentAt).isNull();
    }
    @Test void corruptPayloadRequiresReviewWithoutSending() throws Exception {
        Alert alert=pending(); alert.payload="invalid json"; service.deliver();
        assertThat(alert.status).isEqualTo("UNKNOWN");
        assertThat(alert.deliveryError).isEqualTo("REQUIRES_REVIEW");
        verify(channel,never()).send(any(),anyBoolean());
    }
    @Test void unconfiguredChannelDoesNotClaimAlerts() {
        service.deliver(); verifyNoInteractions(alerts,products);
    }
}
