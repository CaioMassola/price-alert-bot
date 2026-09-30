package com.pricealert.monitor.games;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pricealert.TestSupport;
import com.pricealert.monitor.*;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.product.ProductSnapshot;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GameFeedTest {
    final ObjectMapper mapper=new ObjectMapper();
    final PublicHttpClient http=mock(PublicHttpClient.class);
    final SteamMonitor steam=new SteamMonitor(http,mapper,TestSupport.config());
    final EpicMonitor epic=new EpicMonitor(http,mapper,TestSupport.config());
    ObjectNode steamItem() {
        return mapper.createObjectNode().put("id",123).put("name","Game").put("type",0).put("currency","BRL")
            .put("discounted",true).put("final_price",1000).put("original_price",5000)
            .put("discount_expiration",Instant.now().plusSeconds(3600).getEpochSecond());
    }
    ObjectNode epicItem() {
        ObjectNode item=mapper.createObjectNode().put("id","offer-1").put("title","Game").put("status","ACTIVE")
            .put("productSlug","game/home");
        ObjectNode total=item.putObject("price").putObject("totalPrice");
        total.put("discountPrice",0).put("originalPrice",5999).put("currencyCode","BRL").putObject("currencyInfo").put("decimals",2);
        item.putObject("promotions").putArray("promotionalOffers").addObject().putArray("promotionalOffers").addObject()
            .put("startDate",Instant.now().minusSeconds(3600).toString()).put("endDate",Instant.now().plusSeconds(3600).toString());
        return item;
    }
    void feed(GameFeedMonitor monitor,ObjectNode... items) {
        ObjectNode root=mapper.createObjectNode();
        var array=monitor==steam?root.putObject("specials").putArray("items"):
            root.putObject("data").putObject("Catalog").putObject("searchStore").putArray("elements");
        for(var item:items) array.add(item);
        when(http.get(monitor.getStore(),monitor.feedUrl(),null)).thenReturn(root.toString());
    }
    @Test void collectsDeduplicatedBrazilianOffersAndMatchesTrackedUrls() {
        var item=steamItem(); feed(steam,item,item,steamItem().put("id",456),steamItem().put("discounted",false));
        var offers=steam.searchProducts(MonitorRequest.search("promotions"));
        assertThat(offers).hasSize(2); assertThat(offers.getFirst().currentPrice()).isEqualByComparingTo("10.00");
        assertThat(steam.searchProducts(MonitorRequest.product("https://store.steampowered.com/app/123"))).hasSize(1);
        assertThatThrownBy(()->steam.searchProducts(MonitorRequest.product("https://store.steampowered.com/app/999")))
            .isInstanceOf(StoreAccessException.class);
        assertThatThrownBy(()->steam.searchProducts(MonitorRequest.product("https://evil.test/app/123")))
            .isInstanceOf(IllegalArgumentException.class);
        feed(epic,epicItem());
        var free=epic.searchProducts(MonitorRequest.product("https://store.epicgames.com/pt-BR/p/game")).getFirst();
        assertThat(free.currentPrice()).isZero(); assertThat(free.originalPrice()).isEqualByComparingTo("59.99");
        assertThat(free.store()).isEqualTo(Store.EPIC);
        var mapped=epicItem(); mapped.putObject("catalogNs").putArray("mappings").addObject().put("pageSlug","mapped");
        assertThat(epic.normalize(mapped).url()).endsWith("/mapped");
    }
    @Test void rejectsMalformedFeedsAndAcceptsEmptyLists() {
        for(String response:new String[]{"{","{}"}) {
            when(http.get(Store.STEAM,steam.feedUrl(),null)).thenReturn(response);
            assertThatThrownBy(()->steam.searchProducts(MonitorRequest.search("all"))).isInstanceOf(StoreAccessException.class);
        }
        feed(steam); assertThat(steam.searchProducts(MonitorRequest.search("all"))).isEmpty();
    }
    @Test void rejectsSteamOffersWithoutRealCurrentDiscountInReais() {
        for(String field:new String[]{"id","name","type","currency","discounted","final_price","original_price","discount_expiration"}) {
            var item=steamItem(); item.remove(field); assertThat(steam.normalize(item)).as(field).isNull();
        }
        assertThat(steam.normalize(steamItem().put("original_price",1000))).isNull();
        assertThat(steam.normalize(steamItem().put("final_price",-1))).isNull();
        assertThat(steam.normalize(steamItem().put("final_price",1.5))).isNull();
        assertThat(steam.normalize(steamItem().put("final_price",0)).currentPrice()).isZero();
    }
    @Test void rejectsEpicInvalidMetadataPricesAndInactivePromotions() {
        for(String field:new String[]{"id","title","productSlug","status","promotions"}) {
            var item=epicItem(); item.remove(field); assertThat(epic.normalize(item)).as(field).isNull();
        }
        for(String field:new String[]{"discountPrice","originalPrice","currencyCode","currencyInfo"}) {
            var item=epicItem(); ((ObjectNode)item.path("price").path("totalPrice")).remove(field);
            assertThat(epic.normalize(item)).as(field).isNull();
        }
        var noDiscount=epicItem(); ((ObjectNode)noDiscount.path("price").path("totalPrice")).put("originalPrice",0);
        assertThat(epic.normalize(noDiscount)).isNull();
        assertThat(epic.normalize(epicItem().put("isCodeRedemptionOnly",true))).isNull();
        for(String[] dates:new String[][]{{"bad","bad"},{Instant.now().plusSeconds(300).toString(),Instant.now().plusSeconds(600).toString()},
            {Instant.now().minusSeconds(600).toString(),Instant.now().minusSeconds(300).toString()}}) {
            var item=epicItem(); var promo=(ObjectNode)item.path("promotions").path("promotionalOffers").path(0).path("promotionalOffers").path(0);
            promo.put("startDate",dates[0]).put("endDate",dates[1]); assertThat(epic.normalize(item)).isNull();
        }
        var emptyGroup=epicItem(); ((ObjectNode)emptyGroup.path("promotions").path("promotionalOffers").path(0)).remove("promotionalOffers");
        assertThat(epic.normalize(emptyGroup)).isNull();
    }
    @Test void zeroPricesRequireGameStoreAndPositiveReference() {
        assertThatThrownBy(()->TestSupport.snapshot("-1","100")).isInstanceOf(IllegalArgumentException.class);
        for(Store store:new Store[]{Store.KABUM,Store.STEAM,Store.EPIC}) {
            String url=store==Store.KABUM?"https://www.kabum.com.br/produto/123":store==Store.STEAM?
                "https://store.steampowered.com/app/123":"https://store.epicgames.com/pt-BR/p/game";
            for(BigDecimal reference:new BigDecimal[]{null,BigDecimal.ZERO,BigDecimal.ONE.negate()})
                assertThatThrownBy(()->new ProductSnapshot("123","Game",url,null,BigDecimal.ZERO,reference,store,true,null,Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
