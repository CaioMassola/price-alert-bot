package com.pricealert.monitor;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Component
public class PublicHttpClient {
    private final WebClient client;
    private final MonitorConfig config;
    private final Map<Store,Instant> blockedUntil=new EnumMap<>(Store.class);
    private final Map<Store,Integer> blockedStatus=new EnumMap<>(Store.class);
    private Instant nextRequest=Instant.EPOCH;
    private final Sleeper sleeper;
    @org.springframework.beans.factory.annotation.Autowired
    public PublicHttpClient(WebClient client, MonitorConfig config) { this(client,config,Thread::sleep); }
    PublicHttpClient(WebClient client, MonitorConfig config, Sleeper sleeper) {
        this.client=client; this.config=config; this.sleeper=sleeper;
    }
    @FunctionalInterface interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }
    public synchronized String get(Store store, String url, String token) {
        URI uri=store.validateUrl(url);
        if(blockedUntil.getOrDefault(store,Instant.EPOCH).isAfter(Instant.now()))
            throw new StoreAccessException(blockedStatus.getOrDefault(store,429),"Loja em pausa apos erro ou limite de acesso");
        for(int attempt=0;attempt<3;attempt++) {
            pause();
            Response response;
            try {
                var request=client.get().uri(uri).header("User-Agent","PriceAlertBot/0.1 (personal price monitoring)")
                    .header("Accept","text/html,application/json").header("Accept-Language","pt-BR,pt;q=0.9");
                if(token!=null && !token.isBlank() && "api.mercadolibre.com".equals(uri.getHost()))
                    request=request.header("Authorization","Bearer "+token);
                response=request.exchangeToMono(r->r.bodyToMono(String.class).defaultIfEmpty("")
                    .map(body->new Response(r.statusCode().value(),r.headers().asHttpHeaders().getFirst("Location"),
                        r.headers().asHttpHeaders().getFirst("Retry-After"),body))).block(config.timeout().plusSeconds(2));
            } catch(RuntimeException e) {
                blockedUntil.put(store,Instant.now().plusSeconds(60));
                blockedStatus.put(store,0);
                throw new StoreAccessException(0,"Falha de conexao ou timeout");
            }
            if(response==null) throw new StoreAccessException(0,"Resposta vazia");
            int status=response.status();
            if(status>=300 && status<400 && response.location()!=null) {
                uri=store.validateUrl(uri.resolve(response.location()).toString()); continue;
            }
            if(status==429) {
                blockedUntil.put(store,retryAt(response.retryAfter()));
                blockedStatus.put(store,429);
                throw new StoreAccessException(status,"Limite de acesso; nova tentativa adiada");
            }
            if(status==401 || status==403) {
                blockedUntil.put(store,Instant.now().plus(Duration.ofHours(1)));
                blockedStatus.put(store,status);
                throw new StoreAccessException(status,"Acesso restrito pela loja");
            }
            if(status==408 || status>=500) {
                if(attempt<2) { nextRequest=Instant.now().plusSeconds(30L << attempt); continue; }
                blockedUntil.put(store,Instant.now().plusSeconds(120));
                blockedStatus.put(store,status);
            }
            if(status<200 || status>=300) throw new StoreAccessException(status,"Resposta HTTP nao utilizavel");
            String lower=response.body().toLowerCase(Locale.ROOT);
            if(lower.contains("validatecaptcha") || lower.contains("cf-chl-") || lower.contains("robot check") ||
                lower.contains("verifique se você é humano")) {
                blockedUntil.put(store,Instant.now().plus(Duration.ofHours(1)));
                blockedStatus.put(store,403);
                throw new StoreAccessException(403,"Pagina de verificacao humana; coleta interrompida");
            }
            return response.body();
        }
        throw new StoreAccessException(310,"Redirecionamentos excessivos");
    }
    private void pause() {
        long delay=Duration.between(Instant.now(),nextRequest).toMillis();
        if(delay>0) try { sleeper.sleep(delay); } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new StoreAccessException(0,"Coleta interrompida");
        }
        nextRequest=Instant.now().plus(config.requestGap());
    }
    public static Instant retryAt(String value) {
        try { return Instant.now().plusSeconds(Math.max(60,Long.parseLong(value))); }
        catch(Exception ignored) {
            try { return ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().isAfter(Instant.now())?
                ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant():Instant.now().plusSeconds(60); }
            catch(Exception invalid) { return Instant.now().plusSeconds(300); }
        }
    }
    private record Response(int status, String location, String retryAfter, String body) {}
}

