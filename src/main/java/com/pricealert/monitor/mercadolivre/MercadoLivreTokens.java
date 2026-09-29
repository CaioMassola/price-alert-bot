package com.pricealert.monitor.mercadolivre;

import com.fasterxml.jackson.databind.*;
import com.pricealert.monitor.StoreAccessException;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;

@Component
public class MercadoLivreTokens {
    private final WebClient client;
    private final ObjectMapper mapper;
    private final Path file;
    private final String clientId, secret;
    private String access, refresh;
    private Instant expires, retryAt=Instant.EPOCH;
    private boolean pendingSave;
    public MercadoLivreTokens(WebClient client, ObjectMapper mapper, Environment env) {
        this.client=client; this.mapper=mapper;
        clientId=env.getProperty("mercadolivre.client-id","");
        secret=env.getProperty("mercadolivre.client-secret","");
        file=Path.of(env.getProperty("mercadolivre.token-file",".runtime/oauth/mercadolivre.json"));
        access=env.getProperty("mercadolivre.access-token","");
        refresh=env.getProperty("mercadolivre.refresh-token","");
        try {
            String expiry=env.getProperty("mercadolivre.expires-at","");
            expires=expiry.isBlank()?Instant.EPOCH:Instant.parse(expiry);
            if(Files.exists(file)) {
                JsonNode saved=mapper.readTree(Files.readString(file));
                if(!clientId.equals(saved.path("client_id").asText())) throw new IllegalArgumentException();
                readTokens(saved);
            }
        } catch(Exception e) { throw new IllegalStateException("Estado OAuth local invalido; revise o arquivo privado de tokens"); }
    }
    public synchronized String accessToken() {
        if(pendingSave) persist();
        if(!refresh.isBlank() && !expires.isAfter(Instant.now().plusSeconds(300))) renew();
        return access;
    }
    public synchronized String afterUnauthorized(String rejected) {
        if(access.equals(rejected)) renew();
        return accessToken();
    }
    @Scheduled(fixedDelayString="PT1M",initialDelayString="PT5S")
    public void maintain() {
        try { accessToken(); }
        catch(RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Renovacao OAuth pendente; verifique autorizacao e armazenamento privado");
        }
    }
    private void renew() {
        if(pendingSave) { persist(); return; }
        if(retryAt.isAfter(Instant.now()) || clientId.isBlank() || secret.isBlank() || refresh.isBlank())
            throw new StoreAccessException(401,"Renovacao OAuth indisponivel; confira autorizacao");
        retryAt=Instant.now().plusSeconds(300);
        try {
            JsonNode response=client.post().uri("https://api.mercadolibre.com/oauth/token")
                .body(BodyInserters.fromFormData("grant_type","refresh_token").with("client_id",clientId)
                    .with("client_secret",secret).with("refresh_token",refresh))
                .exchangeToMono(r->{
                    if(!r.statusCode().is2xxSuccessful()) return r.releaseBody().then(reactor.core.publisher.Mono.error(new IllegalStateException()));
                    return r.bodyToMono(JsonNode.class);
                }).block(Duration.ofSeconds(25));
            if(response==null || response.path("expires_in").asLong()<=300 ||
                !response.path("access_token").isTextual() || response.path("access_token").asText().isBlank() ||
                !response.path("refresh_token").isTextual() || response.path("refresh_token").asText().isBlank())
                throw new IllegalStateException();
            access=response.path("access_token").asText(); refresh=response.path("refresh_token").asText();
            expires=Instant.now().plusSeconds(response.path("expires_in").asLong());
            pendingSave=true;
        } catch(Exception e) { throw new StoreAccessException(401,"Falha ao renovar OAuth; nova tentativa adiada"); }
        persist();
    }
    private void readTokens(JsonNode saved) {
        access=saved.path("access_token").asText(); refresh=saved.path("refresh_token").asText();
        expires=Instant.parse(saved.path("expires_at").asText());
        if(access.isBlank() || refresh.isBlank()) throw new IllegalArgumentException();
    }
    private void persist() {
        Path temporary=null;
        try {
            Path directory=file.toAbsolutePath().getParent(); Files.createDirectories(directory);
            boolean posix=Files.getFileStore(directory).supportsFileAttributeView("posix");
            if(posix) Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
            temporary=posix?Files.createTempFile(directory,"tokens-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))):
                Files.createTempFile(directory,"tokens-",".tmp");
            var saved=mapper.createObjectNode().put("client_id",clientId).put("access_token",access)
                .put("refresh_token",refresh).put("expires_at",expires.toString());
            Files.writeString(temporary,mapper.writeValueAsString(saved));
            Files.move(temporary,file.toAbsolutePath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            pendingSave=false;
        } catch(Exception e) { throw new StoreAccessException(401,"Falha ao salvar OAuth; mantenha o bot ativo e verifique o volume"); }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(java.io.IOException ignored) { } }
    }
}
