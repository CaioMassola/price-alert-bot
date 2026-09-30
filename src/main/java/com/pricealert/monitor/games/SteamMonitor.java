package com.pricealert.monitor.games;
import com.fasterxml.jackson.databind.*;
import com.pricealert.monitor.*;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.domain.store.Store;
import com.pricealert.config.MonitorConfig;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class SteamMonitor extends GameFeedMonitor {
    public SteamMonitor(PublicHttpClient http,ObjectMapper mapper,MonitorConfig config) { super(http,mapper,config); }
    public Store getStore() { return Store.STEAM; }
    protected String feedUrl() { return "https://store.steampowered.com/api/featuredcategories?cc=br&l=brazilian"; }
    protected JsonNode entries(JsonNode root) { return root.path("specials").path("items"); }
    protected ProductSnapshot normalize(JsonNode item) {
        var price=cents(item.path("final_price")); var original=cents(item.path("original_price"));
        String id=item.path("id").asText(); String name=item.path("name").asText();
        if(!id.matches("[0-9]+") || name.isBlank() || item.path("type").asInt(-1)!=0 ||
            !"BRL".equals(item.path("currency").asText()) || !item.path("discounted").asBoolean() ||
            price==null || original==null || original.compareTo(price)<=0 ||
            item.path("discount_expiration").asLong()<=Instant.now().getEpochSecond()) return null;
        return new ProductSnapshot(id,name,"https://store.steampowered.com/app/"+id,
            item.path("header_image").asText(null),price,original,getStore(),true,null,Instant.now());
    }
}
