package com.pricealert.monitor.games;
import com.fasterxml.jackson.databind.*;
import com.pricealert.monitor.*;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.config.MonitorConfig;
import java.util.*;
import java.math.BigDecimal;
public abstract class GameFeedMonitor implements StoreMonitor {
    protected final PublicHttpClient http;
    protected final ObjectMapper mapper;
    protected final MonitorConfig config;
    protected GameFeedMonitor(PublicHttpClient http,ObjectMapper mapper,MonitorConfig config) {
        this.http=http; this.mapper=mapper; this.config=config;
    }
    protected abstract String feedUrl();
    protected abstract JsonNode entries(JsonNode root);
    protected abstract ProductSnapshot normalize(JsonNode item);
    public List<ProductSnapshot> searchProducts(MonitorRequest request) {
        String selected=null;
        if(request.productUrl()!=null) {
            getStore().validateUrl(request.productUrl());
            selected=StructuredProductParser.identity(getStore(),request.productUrl());
        }
        try {
            JsonNode items=entries(mapper.readTree(http.get(getStore(),feedUrl(),null)));
            if(!items.isArray()) throw new StoreAccessException(422,"Feed de jogos sem lista reconhecida");
            Map<String,ProductSnapshot> result=new LinkedHashMap<>();
            for(JsonNode item:items) {
                ProductSnapshot product=normalize(item);
                if(product!=null && (selected==null || selected.equals(StructuredProductParser.identity(getStore(),product.url()))))
                    result.putIfAbsent(product.externalId(),product);
            }
            if(selected!=null && result.isEmpty()) throw new StoreAccessException(404,"Produto fora do feed atual de promocoes");
            return result.values().stream().limit(config.maxProducts()).toList();
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new StoreAccessException(422,"Feed de jogos com JSON invalido"); }
    }
    protected BigDecimal cents(JsonNode value) {
        if(!value.isIntegralNumber() || value.asLong()<0) return null;
        return BigDecimal.valueOf(value.asLong(),2);
    }
}
