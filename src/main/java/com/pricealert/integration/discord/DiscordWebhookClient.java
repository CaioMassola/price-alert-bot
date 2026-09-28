package com.pricealert.integration.discord;
import com.pricealert.integration.NotificationException;
import com.pricealert.monitor.PublicHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.time.*;
import java.util.Map;
@Component
public class DiscordWebhookClient {
    private final WebClient client;
    private final String webhook;
    public DiscordWebhookClient(WebClient client,@Value("${discord.webhook-url:}") String webhook) {
        this.client=client; this.webhook=webhook;
        if(!webhook.isBlank()) {
            boolean valid;
            try {
                URI uri=URI.create(webhook);
                valid="https".equals(uri.getScheme()) && "discord.com".equals(uri.getHost()) && uri.getUserInfo()==null &&
                    uri.getPort()==-1 && uri.getQuery()==null && uri.getFragment()==null &&
                    uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_-]+");
            } catch(RuntimeException invalid) { valid=false; }
            if(!valid)
                throw new IllegalArgumentException("DISCORD_WEBHOOK_URL deve ser um webhook HTTPS oficial do Discord");
        }
    }
    public boolean configured() { return !webhook.isBlank(); }
    public void send(Map<String,Object> payload) {
        if(!configured()) throw new NotificationException("NOT_CONFIGURED",null);
        try {
            Result result=client.post().uri(webhook+"?wait=true").bodyValue(payload).exchangeToMono(response->
                response.bodyToMono(String.class).defaultIfEmpty("").map(body->new Result(
                    response.statusCode().value(),response.headers().asHttpHeaders().getFirst("Retry-After"),body)))
                .block(Duration.ofSeconds(25));
            if(result==null) throw new NotificationException("UNKNOWN",null);
            if(result.status()==429) throw new NotificationException("RATE_LIMITED",PublicHttpClient.retryAt(result.retryAfter()));
            if(result.status()>=400 && result.status()<500) throw new NotificationException("FAILED",null);
            if(result.status()<200 || result.status()>=300 || !result.body().contains("\"id\""))
                throw new NotificationException("UNKNOWN",null);
        } catch(NotificationException e) { throw e; }
        catch(RuntimeException e) { throw new NotificationException("UNKNOWN",null); }
    }
    private record Result(int status,String retryAfter,String body) {}
}

