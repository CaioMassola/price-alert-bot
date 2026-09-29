package com.pricealert.monitor.mercadolivre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricealert.monitor.StoreAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MercadoLivreTokensTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper();
    MockEnvironment env() {
        return new MockEnvironment().withProperty("mercadolivre.client-id","test-client")
            .withProperty("mercadolivre.client-secret","test-secret")
            .withProperty("mercadolivre.access-token","old-access")
            .withProperty("mercadolivre.refresh-token","old-refresh")
            .withProperty("mercadolivre.expires-at","2000-01-01T00:00:00Z")
            .withProperty("mercadolivre.token-file",directory.resolve("tokens.json").toString());
    }
    WebClient web(AtomicInteger calls, HttpStatus status, String body) {
        return WebClient.builder().exchangeFunction(r->{
            calls.incrementAndGet(); assertThat(r.url().toString()).isEqualTo("https://api.mercadolibre.com/oauth/token");
            return Mono.just(ClientResponse.create(status).header("Content-Type","application/json").body(body).build());
        }).build();
    }
    String success() { return "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":21600}"; }
    @Test void rotatesOnceForConcurrentCallsAndRestoresFromDisk() throws Exception {
        var calls=new AtomicInteger(); var client=web(calls,HttpStatus.OK,success());
        var tokens=new MercadoLivreTokens(client,mapper,env());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(tokens::accessToken); var second=executor.submit(tokens::accessToken);
            assertThat(first.get()).isEqualTo("new-access"); assertThat(second.get()).isEqualTo("new-access");
        }
        assertThat(calls.get()).isEqualTo(1);
        var saved=mapper.readTree(Files.readString(directory.resolve("tokens.json")));
        assertThat(saved.path("refresh_token").asText()).isEqualTo("new-refresh");
        var restarted=new MercadoLivreTokens(client,mapper,env());
        assertThat(restarted.accessToken()).isEqualTo("new-access");
        assertThat(restarted.afterUnauthorized("old-access")).isEqualTo("new-access");
        restarted.maintain(); assertThat(calls.get()).isEqualTo(1);
    }
    @Test void refreshesRejectedTokenEvenBeforeExpiry() {
        var calls=new AtomicInteger(); var tokens=new MercadoLivreTokens(web(calls,HttpStatus.OK,success()),mapper,
            env().withProperty("mercadolivre.expires-at",Instant.now().plusSeconds(3600).toString()));
        assertThat(tokens.accessToken()).isEqualTo("old-access"); assertThat(calls.get()).isZero();
        assertThat(tokens.afterUnauthorized("old-access")).isEqualTo("new-access");
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void failedRequestsBackOffAndDoNotOverwriteCredentials() {
        var calls=new AtomicInteger(); var tokens=new MercadoLivreTokens(web(calls,HttpStatus.BAD_REQUEST,"private response"),mapper,env());
        assertThatThrownBy(tokens::accessToken).isInstanceOf(StoreAccessException.class).hasMessageNotContaining("private");
        tokens.maintain(); assertThat(calls.get()).isEqualTo(1); assertThat(directory.resolve("tokens.json")).doesNotExist();
    }
    @Test void malformedResponsesAndMissingConfigurationFailSafely() {
        var calls=new AtomicInteger(); var tokens=new MercadoLivreTokens(web(calls,HttpStatus.OK,"{}"),mapper,env());
        assertThatThrownBy(tokens::accessToken).isInstanceOf(StoreAccessException.class);
        assertThat(directory.resolve("tokens.json")).doesNotExist();
        var missing=new MercadoLivreTokens(web(calls,HttpStatus.OK,success()),mapper,env().withProperty("mercadolivre.client-secret",""));
        assertThatThrownBy(missing::accessToken).isInstanceOf(StoreAccessException.class); assertThat(calls.get()).isEqualTo(1);
        var absent=new MercadoLivreTokens(web(calls,HttpStatus.OK,success()),mapper,new MockEnvironment()
            .withProperty("mercadolivre.token-file",directory.resolve("empty.json").toString()));
        assertThat(absent.accessToken()).isEmpty();
    }
    @Test void retriesPersistenceWithoutReusingRotatedRefreshToken() throws Exception {
        Path blocker=directory.resolve("blocked"); Files.writeString(blocker,"file");
        var config=env().withProperty("mercadolivre.token-file",blocker.resolve("tokens.json").toString());
        var calls=new AtomicInteger(); var tokens=new MercadoLivreTokens(web(calls,HttpStatus.OK,success()),mapper,config);
        assertThatThrownBy(tokens::accessToken).isInstanceOf(StoreAccessException.class);
        Files.delete(blocker);
        assertThat(tokens.accessToken()).isEqualTo("new-access"); assertThat(calls.get()).isEqualTo(1);
        assertThat(new MercadoLivreTokens(web(calls,HttpStatus.OK,success()),mapper,config).accessToken()).isEqualTo("new-access");
    }
    @Test void refusesCorruptOrForeignPersistedState() throws Exception {
        Files.writeString(directory.resolve("tokens.json"),"{\"client_id\":\"another-client\"}");
        assertThatThrownBy(()->new MercadoLivreTokens(WebClient.create(),mapper,env())).isInstanceOf(IllegalStateException.class);
        Files.writeString(directory.resolve("tokens.json"),"{\"client_id\":\"test-client\",\"expires_at\":\"2099-01-01T00:00:00Z\"}");
        assertThatThrownBy(()->new MercadoLivreTokens(WebClient.create(),mapper,env())).isInstanceOf(IllegalStateException.class);
    }
}
