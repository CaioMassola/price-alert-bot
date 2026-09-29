package com.pricealert.monitor;
import com.pricealert.TestSupport;
import com.pricealert.domain.store.Store;
import com.pricealert.monitor.mercadolivre.MercadoLivreMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLivreCatalogTest {
    @Test void publicFallbackCanReturnAValidStructuredProduct() {
        var fallback=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,TestSupport.tokens(""));
        when(http.get(eq(Store.MERCADO_LIVRE),anyString(),isNull())).thenReturn("""
            <script type="application/ld+json">{"@type":"Product","name":"Product","sku":"MLB123",
            "url":"https://produto.mercadolivre.com.br/MLB-123-product","offers":{"price":100,"priceCurrency":"BRL","availability":"InStock"}}</script>
            """);
        assertThat(fallback.searchProducts(MonitorRequest.search("product")).getFirst().externalId()).isEqualTo("MLB123");
    }
    @Test void validatesCatalogShapeIdentifiersAndActiveStatus() {
        String search="/products/search?status=active&site_id=MLB&q=teclado&limit=5";
        for(String body:java.util.List.of("{}","{\"results\":[{\"id\":\"invalid\"}]}")) {
            response(search,body);
            assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
        }
        catalog(); response("/products/MLB123","{\"status\":\"inactive\"}");
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
        catalog(); response("/products/MLB123/items?limit=5","{}");
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
    }
    @Test void catalogSelectionAndInvalidSellerOffers() {
        catalog();
        assertThat(monitor.searchProducts(MonitorRequest.product("https://www.mercadolivre.com.br/produto/p/MLB123"))).hasSize(1);
        assertThat(monitor.searchProducts(MonitorRequest.product("https://www.mercadolivre.com.br/produto/p/MLB123?wid=MLB456"))).hasSize(1);
        response("/products/MLB123/items?limit=5","{\"results\":[{\"item_id\":\"bad\"},{\"item_id\":\"MLB1\",\"currency_id\":\"USD\"}]}");
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
    }
    @Test void searchBoundsCatalogRequestsAndDeduplicatesSellerItems() {
        catalog();
        response("/products/search?status=active&site_id=MLB&q=teclado&limit=5","{\"results\":["+String.join(",",java.util.Collections.nCopies(6,"{\"id\":\"MLB123\"}"))+"]}");
        response("/products/MLB123/items?limit=4","{\"results\":[{\"item_id\":\"MLB456\",\"condition\":\"new\",\"currency_id\":\"BRL\",\"price\":100}]}");
        assertThat(monitor.searchProducts(MonitorRequest.search("teclado"))).hasSize(1);
        verify(http,times(5)).get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/products/MLB123","test-token");
        String offers=java.util.stream.IntStream.range(1,7).mapToObj(i->"{\"item_id\":\"MLB"+i+"\",\"condition\":\"new\",\"currency_id\":\"BRL\",\"price\":100}").collect(java.util.stream.Collectors.joining(","));
        response("/products/MLB123/items?limit=5","{\"results\":["+offers+"]}");
        assertThat(monitor.searchProducts(MonitorRequest.search("teclado"))).hasSize(5);
    }
    @Test void normalizerRejectsCurrencyAndMarksZeroStockOrClosedItemsUnavailable() throws Exception {
        assertThatThrownBy(()->monitor.normalize(mapper.readTree("{}"))).isInstanceOf(StoreAccessException.class);
        for(String state:java.util.List.of("\"available_quantity\":0","\"available_quantity\":1,\"status\":\"closed\"")) {
            var result=monitor.normalize(mapper.readTree("{\"id\":\"MLB1\",\"title\":\"Product\",\"permalink\":\"https://produto.mercadolivre.com.br/MLB-1\",\"price\":100,\"currency_id\":\"BRL\","+state+"}"));
            assertThat(result.available()).isFalse();
        }
    }
    @Test void fallbackRecognizesCatalogLinksAndIgnoresOtherLinks() {
        var fallback=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,TestSupport.tokens(""));
        when(http.get(eq(Store.MERCADO_LIVRE),anyString(),isNull())).thenReturn("<a href='https://www.mercadolivre.com.br/help'>help</a><a href='https://www.mercadolivre.com.br/product/p/MLB123'>product</a>","<html></html>");
        assertThatThrownBy(()->fallback.searchProducts(MonitorRequest.search("phone"))).isInstanceOf(StoreAccessException.class);
        verify(http).get(Store.MERCADO_LIVRE,"https://www.mercadolivre.com.br/product/p/MLB123",null);
    }
    @Test void unauthorizedRequestRenewsAndRetriesWithNewToken() {
        var provider=TestSupport.tokens("old"); when(provider.afterUnauthorized("old")).thenReturn("new");
        var renewing=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,provider);
        when(http.get(eq(Store.MERCADO_LIVRE),anyString(),eq("old"))).thenThrow(new StoreAccessException(401,"expired"));
        when(http.get(eq(Store.MERCADO_LIVRE),anyString(),eq("new"))).thenReturn("{\"results\":[]}");
        assertThatThrownBy(()->renewing.searchProducts(MonitorRequest.search("teclado")))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(422));
        verify(provider).afterUnauthorized("old"); verify(http).authenticationRenewed(Store.MERCADO_LIVRE);
        verify(http).get(eq(Store.MERCADO_LIVRE),anyString(),eq("new"));
    }
    @Test void rejectsMalformedApiAndPropagatesOfferRestrictions() {
        response("/products/search?status=active&site_id=MLB&q=teclado&limit=5","{");
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado")))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(422));
        catalog();
        when(http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/products/MLB123/items?limit=5","test-token"))
            .thenThrow(new StoreAccessException(403,"denied"));
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado")))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(403));
    }
    @Test void fetchesTrackedListingByItemIdentity() throws Exception {
        try(var stream=getClass().getResourceAsStream("/fixtures/mercadolivre.json")) {
            response("/items/MLB123",new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(monitor.searchProducts(MonitorRequest.product("https://produto.mercadolivre.com.br/MLB-123-product"))).hasSize(1);
    }
    @Test void publicFallbackRecognizesProductLinksWithoutApiToken() {
        var fallback=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,TestSupport.tokens(""));
        when(http.get(Store.MERCADO_LIVRE,"https://lista.mercadolivre.com.br/teclado",null))
            .thenReturn("<a href='https://produto.mercadolivre.com.br/MLB-123-keyboard'>product</a>");
        when(http.get(Store.MERCADO_LIVRE,"https://produto.mercadolivre.com.br/MLB-123-keyboard",null)).thenReturn("<html></html>");
        assertThatThrownBy(()->fallback.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
        verify(http).get(Store.MERCADO_LIVRE,"https://produto.mercadolivre.com.br/MLB-123-keyboard",null);
    }
    private final PublicHttpClient http=mock(PublicHttpClient.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final MercadoLivreMonitor monitor=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,TestSupport.tokens("test-token"));
    private void response(String path,String body) {
        when(http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com"+path,"test-token")).thenReturn(body);
    }
    private void catalog() {
        response("/products/search?status=active&site_id=MLB&q=teclado&limit=5","{\"results\":[{\"id\":\"MLB123\"}]}");
        response("/products/MLB123","""
            {"id":"MLB123","status":"active","name":"Produto teste","permalink":"https://www.mercadolivre.com.br/produto/p/MLB123","buy_box_winner":null}
            """);
        response("/products/MLB123/items?limit=5","""
            {"results":[
              {"item_id":"MLB456","condition":"new","currency_id":"BRL","price":100,"original_price":null},
              {"item_id":"MLB789","condition":"used","currency_id":"BRL","price":50},
              {"item_id":"MLB999","condition":"new","currency_id":"BRL","price":40,"min_purchase_unit":10}
            ]}
            """);
    }
    @Test void usesCompetingOffersEvenWithoutWinnerAndKeepsSellerIdentity() {
        catalog();
        var result=monitor.searchProducts(MonitorRequest.search("teclado"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().externalId()).isEqualTo("MLB456");
        assertThat(result.getFirst().url()).endsWith("?wid=MLB456");
        assertThat(result.getFirst().currentPrice()).isEqualByComparingTo("100");
        assertThat(result.getFirst().originalPrice()).isNull();
        verify(http,never()).get(eq(Store.MERCADO_LIVRE),contains("/sites/MLB/search"),anyString());
    }
    @Test void missingSelectedSellerCannotBecomeAnotherSellerTargetAlert() {
        catalog();
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.product("https://www.mercadolivre.com.br/produto/p/MLB123?wid=MLB777")))
            .isInstanceOf(StoreAccessException.class);
    }
    @Test void emptyPermalinkUsesCatalogIdentityRatherThanFailingAllDiscovery() {
        catalog();
        response("/products/MLB123","{\"status\":\"active\",\"name\":\"Produto\",\"permalink\":\"\"}");
        assertThat(monitor.searchProducts(MonitorRequest.search("teclado")).getFirst().url())
            .isEqualTo("https://www.mercadolivre.com.br/p/MLB123?wid=MLB456");
    }
    @Test void accessDenialIsNotRetriedThroughAnotherSource() {
        when(http.get(eq(Store.MERCADO_LIVRE),anyString(),anyString())).thenThrow(new StoreAccessException(403,"Denied"));
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("teclado"))).isInstanceOf(StoreAccessException.class);
        verify(http,times(1)).get(any(),anyString(),anyString());
    }
    @Test void missingOffersDoesNotAbortTheNextCatalogProduct() {
        catalog();
        response("/products/search?status=active&site_id=MLB&q=teclado&limit=5","{\"results\":[{\"id\":\"MLB124\"},{\"id\":\"MLB123\"}]}");
        response("/products/MLB124","{\"status\":\"active\",\"name\":\"Sem ofertas\",\"permalink\":\"\"}");
        when(http.get(Store.MERCADO_LIVRE,"https://api.mercadolibre.com/products/MLB124/items?limit=5","test-token"))
            .thenThrow(new StoreAccessException(404,"Not found"));
        assertThat(monitor.searchProducts(MonitorRequest.search("teclado"))).hasSize(1);
    }
}
