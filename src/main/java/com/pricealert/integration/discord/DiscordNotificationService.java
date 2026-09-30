package com.pricealert.integration.discord;
import com.pricealert.domain.store.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;
@Service
public class DiscordNotificationService {
    private final DiscordWebhookClient defaultClient;
    private final DiscordWebhookClient gamesClient;
    @Autowired
    public DiscordNotificationService(WebClient client,
        @Value("${discord.webhook-url:}") String defaultWebhook,
        @Value("${discord.webhook-games:}") String gamesWebhook) {
        this(new DiscordWebhookClient(client,defaultWebhook),new DiscordWebhookClient(client,gamesWebhook));
    }
    public DiscordNotificationService(DiscordWebhookClient defaultClient,DiscordWebhookClient gamesClient) {
        this.defaultClient=defaultClient; this.gamesClient=gamesClient;
    }
    private DiscordWebhookClient client(Store store) { return store.isGameStore()?gamesClient:defaultClient; }
    public boolean configured() { return defaultClient.configured() || gamesClient.configured(); }
    public boolean configured(Store store) { return client(store).configured(); }
    public void notify(Map<String,Object> payload,Store store) { client(store).send(payload); }
}
