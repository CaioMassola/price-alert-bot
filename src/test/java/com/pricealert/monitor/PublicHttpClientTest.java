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

