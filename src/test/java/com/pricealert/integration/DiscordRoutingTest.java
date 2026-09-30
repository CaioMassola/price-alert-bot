package com.pricealert.integration;
import com.pricealert.domain.store.Store;
import com.pricealert.integration.discord.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiscordRoutingTest {
    @Test void routesExclusivelyToStoreChannelAndReportsConfiguration() {
        var main=mock(DiscordWebhookClient.class); var games=mock(DiscordWebhookClient.class);
        var service=new DiscordNotificationService(main,games);
        var channel=new DiscordNotificationChannel(service);
        assertThat(service.configured()).isFalse();
        when(games.configured()).thenReturn(true);
        assertThat(service.configured()).isTrue();
        assertThat(channel.configured(Store.STEAM)).isTrue();
        assertThat(channel.configured(Store.KABUM)).isFalse();
        when(main.configured()).thenReturn(true); assertThat(service.configured()).isTrue();
        for(Store store:Store.values()) service.notify(Map.of("content",store.name()),store);
        verify(main).send(Map.of("content","KABUM"));
        verify(main).send(Map.of("content","MERCADO_LIVRE"));
        verify(main).send(Map.of("content","AMAZON"));
        verify(games).send(Map.of("content","STEAM")); verify(games).send(Map.of("content","EPIC"));
        verify(main,times(3)).send(any()); verify(games,times(2)).send(any());
    }
    @Test void absentGamesWebhookNeverFallsBackToMain() {
        var service=new DiscordNotificationService(WebClient.create(),"https://discord.com/api/webhooks/123/test-token","");
        assertThat(service.configured(Store.STEAM)).isFalse();
        assertThatThrownBy(()->service.notify(Map.of(),Store.STEAM)).isInstanceOfSatisfying(NotificationException.class,
            e->assertThat(e.state).isEqualTo("NOT_CONFIGURED"));
        NotificationChannel fallback=new NotificationChannel() {
            public boolean configured() { return true; }
            public void send(com.pricealert.domain.alert.PriceAlert alert) { }
        };
        assertThat(fallback.configured(Store.KABUM)).isTrue();
    }
}
