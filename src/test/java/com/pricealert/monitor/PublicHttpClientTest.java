package com.pricealert.monitor;
import com.pricealert.TestSupport;
import com.pricealert.domain.store.Store;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class PublicHttpClientTest {
    @Test void newTokenClearsOnlyAuthenticationPause() {
        var calls=new AtomicInteger();
        var web=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(
            calls.incrementAndGet()==1?HttpStatus.UNAUTHORIZED:HttpStatus.OK).body("{}").build())).build();
        var http=new PublicHttpClient(web,TestSupport.config());
        assertThatThrownBy(()->http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/users/me","old")).isInstanceOf(StoreAccessException.class);
        assertThat(http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/users/me","new")).isEqualTo("{}");
        assertThat(calls.get()).isEqualTo(2);
        http.authenticationRenewed(Store.KABUM);
    }
    @Test void retriesServerErrorsWithIncreasingDelayThenBlocksStore() {
        var calls=new AtomicInteger(); var delays=new java.util.ArrayList<Long>();
        var web=WebClient.builder().exchangeFunction(request->{calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());}).build();
        var http=new PublicHttpClient(web,TestSupport.config(),delays::add);
        assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(503));
        assertThat(calls.get()).isEqualTo(3); assertThat(delays).hasSize(2);
        assertThat(delays.get(0)).isBetween(29000L,30000L); assertThat(delays.get(1)).isBetween(59000L,60000L);
        assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null)).isInstanceOf(StoreAccessException.class);
        assertThat(calls.get()).isEqualTo(3);
    }
    @Test void preservesThreadInterruptionDuringBackoff() {
        var web=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())).build();
        var http=new PublicHttpClient(web,TestSupport.config(),delay->{throw new InterruptedException();});
        try {
            assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null)).hasMessage("Coleta interrompida");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
    @Test void sendsTokenOnlyToOfficialApiAndReturnsBody() {
        var requests=new java.util.ArrayList<ClientRequest>();
        var web=WebClient.builder().exchangeFunction(r->{requests.add(r); return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());}).build();
        var http=new PublicHttpClient(web,TestSupport.config());
        assertThat(http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/users/me","test-token")).isEqualTo("{}");
        http.get(Store.KABUM,"https://www.kabum.com.br/produto/1","test-token");
        assertThat(requests.get(0).headers().getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(requests.get(1).headers().containsKey("Authorization")).isFalse();
    }
    @Test void rejectsCaptchaAndRedirectLoops() {
        var captcha=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(HttpStatus.OK).body("robot check").build())).build();
        assertThatThrownBy(()->new PublicHttpClient(captcha,TestSupport.config()).get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(403));
        var loop=WebClient.builder().exchangeFunction(r->Mono.just(ClientResponse.create(HttpStatus.FOUND).header("Location","/produto/1").build())).build();
        assertThatThrownBy(()->new PublicHttpClient(loop,TestSupport.config()).get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(310));
        assertThat(PublicHttpClient.retryAt("invalid")).isAfter(Instant.now().plusSeconds(290));
    }
    @Test void rateLimitStopsFurtherRequests() {
        var calls=new AtomicInteger();
        var web=WebClient.builder().exchangeFunction(request->{
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","120").body("limited").build());
        }).build();
        var http=new PublicHttpClient(web,TestSupport.config());
        for(int i=0;i<2;i++) assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOf(StoreAccessException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void forbiddenDoesNotRetry() {
        var calls=new AtomicInteger();
        var web=WebClient.builder().exchangeFunction(request->{ calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN).build()); }).build();
        var http=new PublicHttpClient(web,TestSupport.config());
        assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOf(StoreAccessException.class).hasMessage("Acesso restrito pela loja");
        assertThat(calls.get()).isEqualTo(1);
        assertThatThrownBy(()->http.get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(403));
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void blocksCrossStoreRedirect() {
        var web=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.FOUND)
            .header("Location","https://127.0.0.1/private").build())).build();
        assertThatThrownBy(()->new PublicHttpClient(web,TestSupport.config()).get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void handlesMissingProductAndTimeout() {
        var missing=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build())).build();
        assertThatThrownBy(()->new PublicHttpClient(missing,TestSupport.config()).get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(404));
        var timeout=WebClient.builder().exchangeFunction(request->Mono.error(new RuntimeException("sensitive URL"))).build();
        assertThatThrownBy(()->new PublicHttpClient(timeout,TestSupport.config()).get(Store.KABUM,"https://www.kabum.com.br/produto/1",null))
            .hasMessage("Falha de conexao ou timeout");
    }
    @Test void retryAfterDateIsRespected() {
        Instant future=Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var value=java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(future.atZone(ZoneOffset.UTC));
        assertThat(PublicHttpClient.retryAt(value)).isEqualTo(future);
    }
}

