package com.pricealert.config;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
@Configuration
public class WebClientConfig {
    @Bean public WebClient webClient(MonitorConfig config) {
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .resolver(io.netty.resolver.DefaultAddressResolverGroup.INSTANCE)
            .followRedirect(false).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000)
            .responseTimeout(config.timeout())))
            .codecs(c->c.defaultCodecs().maxInMemorySize(8*1024*1024)).build();
    }
}

