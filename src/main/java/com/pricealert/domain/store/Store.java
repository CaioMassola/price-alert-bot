package com.pricealert.domain.store;
import java.net.URI;
import java.util.Set;
public enum Store {
    MERCADO_LIVRE(Set.of("www.mercadolivre.com.br", "produto.mercadolivre.com.br", "lista.mercadolivre.com.br", "api.mercadolibre.com")),
    KABUM(Set.of("www.kabum.com.br")),
    AMAZON(Set.of("www.amazon.com.br")),
    STEAM(Set.of("store.steampowered.com")),
    EPIC(Set.of("store.epicgames.com", "store-site-backend-static.ak.epicgames.com"));
    public boolean isGameStore() { return this==STEAM || this==EPIC; }
    private final Set<String> hosts;
    Store(Set<String> hosts) { this.hosts = hosts; }
    public URI validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !hosts.contains(uri.getHost()) ||
                uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null)
                throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException e) { throw new IllegalArgumentException("URL HTTPS deve pertencer a loja selecionada"); }
    }
}

