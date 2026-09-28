package com.pricealert.integration;
import com.pricealert.TestSupport;
import com.pricealert.domain.alert.PriceAlert;
import com.pricealert.integration.discord.*;
import com.pricealert.service.DiscountService;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class DiscordTest {
    @Test void introductionAppearsAboveOfferOnlyWhenRequested() {
        var client=org.mockito.Mockito.mock(DiscordWebhookClient.class);
        var channel=new DiscordNotificationChannel(client);
        var product=TestSupport.snapshot("50","100");
        var analysis=new DiscountService(TestSupport.config()).analyze(null,product,List.of(),null,false);
        var alert=new PriceAlert(1L,product,analysis,product.currentPrice());
        channel.send(alert,true); channel.send(alert,false);
        var messages=org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).get("content")).isEqualTo("Olá, soldados! Tudo bem? Encontrei novas promoções! 💜");
        assertThat(messages.getAllValues().get(0)).containsKey("embeds");
        assertThat(messages.getAllValues().get(1)).doesNotContainKey("content");
    }
    private final String webhook="https://discord.com/api/webhooks/123/test-token";
    @Test void requestsServerAcknowledgementAndAcceptsOnlyConfirmedMessage() {
        var captured=new AtomicReference<ClientRequest>();
        var client=WebClient.builder().exchangeFunction(request->{
            captured.set(request); return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"id\":\"12345\"}").build());
        }).build();
        var channel=new DiscordNotificationChannel(new DiscordWebhookClient(client,webhook));
        var product=TestSupport.snapshot("50","100");
        var analysis=new DiscountService(TestSupport.config()).analyze(null,product,List.of(),null,false);
        var alert=new PriceAlert(1L,product,analysis,product.currentPrice());
        channel.send(alert);
        assertThat(captured.get().url().getQuery()).isEqualTo("wait=true");
        assertThat(channel.payload(alert)).containsKey("allowed_mentions");
    }
    @Test void timeoutIsAmbiguousAndDoesNotLeakWebhook() {
        var client=WebClient.builder().exchangeFunction(request->Mono.error(new IllegalStateException(webhook))).build();
        assertThatThrownBy(()->new DiscordWebhookClient(client,webhook).send(java.util.Map.of("content","test")))
            .isInstanceOfSatisfying(NotificationException.class,e->assertThat(e.state).isEqualTo("UNKNOWN"))
            .hasMessageNotContaining("test-token");
    }
    @Test void rateLimitIsDeferredAndNeverSent() {
        var client=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After","120").build())).build();
        assertThatThrownBy(()->new DiscordWebhookClient(client,webhook).send(java.util.Map.of("content","test")))
            .isInstanceOfSatisfying(NotificationException.class,e->{
                assertThat(e.state).isEqualTo("RATE_LIMITED"); assertThat(e.retryAt).isAfter(java.time.Instant.now().plusSeconds(100));
            });
    }
}
