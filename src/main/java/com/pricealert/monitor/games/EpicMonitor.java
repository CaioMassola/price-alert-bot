package com.pricealert.monitor.games;
import com.fasterxml.jackson.databind.*;
import com.pricealert.monitor.*;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.domain.store.Store;
import com.pricealert.config.MonitorConfig;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class EpicMonitor extends GameFeedMonitor {
    public EpicMonitor(PublicHttpClient http,ObjectMapper mapper,MonitorConfig config) { super(http,mapper,config); }
    public Store getStore() { return Store.EPIC; }
    protected String feedUrl() { return "https://store-site-backend-static.ak.epicgames.com/freeGamesPromotions?locale=pt-BR&country=BR&allowCountries=BR"; }
    protected JsonNode entries(JsonNode root) { return root.path("data").path("Catalog").path("searchStore").path("elements"); }
    protected ProductSnapshot normalize(JsonNode item) {
        JsonNode total=item.path("price").path("totalPrice");
        var price=cents(total.path("discountPrice")); var original=cents(total.path("originalPrice"));
        String id=item.path("id").asText(), name=item.path("title").asText();
        String slug=item.path("catalogNs").path("mappings").path(0).path("pageSlug").asText();
        if(slug.isBlank()) slug=item.path("productSlug").asText().replaceFirst("/home$","");
        if(id.isBlank() || name.isBlank() || !slug.matches("[a-z0-9-]+") ||
            !"ACTIVE".equals(item.path("status").asText()) || !"BRL".equals(total.path("currencyCode").asText()) ||
            total.path("currencyInfo").path("decimals").asInt(-1)!=2 || item.path("isCodeRedemptionOnly").asBoolean() ||
            price==null || original==null || original.compareTo(price)<=0 || !activePromotion(item)) return null;
        return new ProductSnapshot(id,name,"https://store.epicgames.com/pt-BR/p/"+slug,
            item.path("keyImages").path(0).path("url").asText(null),price,original,getStore(),true,null,Instant.now());
    }
    private boolean activePromotion(JsonNode item) {
        Instant now=Instant.now();
        for(JsonNode group:item.path("promotions").path("promotionalOffers"))
            for(JsonNode promotion:group.path("promotionalOffers")) {
                try {
                    Instant start=Instant.parse(promotion.path("startDate").asText());
                    Instant end=Instant.parse(promotion.path("endDate").asText());
                    if(!now.isBefore(start) && now.isBefore(end)) return true;
                } catch(java.time.format.DateTimeParseException ignored) { }
            }
        return false;
    }
}
