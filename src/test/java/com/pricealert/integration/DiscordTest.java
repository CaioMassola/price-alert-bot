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
    @Test void webhookValidationRejectsEveryUntrustedUriComponent() {
        for(String url:List.of("https://user@discord.com/api/webhooks/123/token","https://discord.com:443/api/webhooks/123/token",
            webhook+"?wait=true",webhook+"#fragment","https://discord.com/api/other"))
            assertThatThrownBy(()->new DiscordWebhookClient(WebClient.create(),url)).isInstanceOf(IllegalArgumentException.class);
        var empty=new DiscordWebhookClient(WebClient.create(),"");
        assertThat(empty.configured()).isFalse();
        assertThatThrownBy(()->empty.send(java.util.Map.of())).isInstanceOfSatisfying(NotificationException.class,e->assertThat(e.state).isEqualTo("NOT_CONFIGURED"));
    }
    @Test void nonSuccessfulResponsesAreNotConfirmedDeliveries() {
        for(int code:new int[]{199,302,400}) {
            var web=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(org.springframework.http.HttpStatusCode.valueOf(code)).body("{\"id\":\"1\"}").build())).build();
            assertThatThrownBy(()->new DiscordWebhookClient(web,webhook).send(java.util.Map.of()))
                .isInstanceOfSatisfying(NotificationException.class,e->assertThat(e.state).isEqualTo(code==400?"FAILED":"UNKNOWN"));
        }
        var web=org.mockito.Mockito.mock(WebClient.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(web.post().uri(webhook+"?wait=true").bodyValue(java.util.Map.of()).exchangeToMono(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        assertThatThrownBy(()->new DiscordWebhookClient(web,webhook).send(java.util.Map.of()))
            .isInstanceOfSatisfying(NotificationException.class,e->assertThat(e.state).isEqualTo("UNKNOWN"));
    }
    @Test void rendersHistoricalPricesImageAndFixedCouponForOtherStores() {
        var channel=new DiscordNotificationChannel(org.mockito.Mockito.mock(DiscordWebhookClient.class));
        var coupon=new com.pricealert.domain.coupon.Coupon("FIXED",null,java.math.BigDecimal.TEN,null,null,"source");
        var product=new com.pricealert.domain.product.ProductSnapshot("MLB1","Product","https://produto.mercadolivre.com.br/MLB-1","https://image.test/a",
            new java.math.BigDecimal("100"),null,com.pricealert.domain.store.Store.MERCADO_LIVRE,true,coupon,java.time.Instant.now());
        var previous=new com.pricealert.domain.product.Product(); previous.currentPrice=new java.math.BigDecimal("200");
        var analysis=new DiscountService(TestSupport.config()).analyze(previous,product,TestSupport.history("200","200","200"),null,false);
        var payload=channel.payload(new PriceAlert(1L,product,analysis,new java.math.BigDecimal("90"))).toString();
        assertThat(payload).contains("thumbnail","FIXED","90,00","50.00%").doesNotContain("Pagamento");
    }
    @Test void rejectsUnofficialWebhooksAndUnconfirmedResponses() {
        for(String url:List.of("http://discord.com/api/webhooks/123/token","https://evil.test/webhook","invalid url"))
            assertThatThrownBy(()->new DiscordWebhookClient(WebClient.create(),url)).isInstanceOf(IllegalArgumentException.class);
        for(HttpStatus status:List.of(HttpStatus.OK,HttpStatus.INTERNAL_SERVER_ERROR)) {
            var web=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(status).body("{}").build())).build();
            assertThatThrownBy(()->new DiscordWebhookClient(web,webhook).send(java.util.Map.of()))
                .isInstanceOfSatisfying(NotificationException.class,e->assertThat(e.state).isEqualTo("UNKNOWN"));
        }
    }
    @Test void rendersCouponTermsAndSupportsChannelsWithoutIntroduction() {
        var client=org.mockito.Mockito.mock(DiscordWebhookClient.class);
        org.mockito.Mockito.when(client.configured()).thenReturn(true);
        var channel=new DiscordNotificationChannel(client); assertThat(channel.configured()).isTrue();
        for(var percentage:java.util.Arrays.asList(java.math.BigDecimal.TEN,null)) {
            var coupon=new com.pricealert.domain.coupon.Coupon("SALE",percentage,null,null,null,"https://www.kabum.com.br/produto/123");
            var product=new com.pricealert.domain.product.ProductSnapshot("123","Keyboard",coupon.source(),null,
                new java.math.BigDecimal("100"),new java.math.BigDecimal("200"),com.pricealert.domain.store.Store.KABUM,true,coupon,java.time.Instant.now());
            var analysis=new DiscountService(TestSupport.config()).analyze(null,product,List.of(),null,false);
            var alert=new PriceAlert(1L,product,analysis,product.currentPrice());
            assertThat(channel.payload(alert).toString()).contains("SALE");
            NotificationChannel fallback=new NotificationChannel() {
                public boolean configured() { return true; }
                public void send(PriceAlert value) { channel.send(value); }
            };
            fallback.send(alert,true);
        }
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any());
    }
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
