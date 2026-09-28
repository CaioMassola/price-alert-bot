package com.pricealert.monitor;
import com.pricealert.TestSupport;
import com.pricealert.domain.store.Store;
import com.pricealert.monitor.mercadolivre.MercadoLivreMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLivreCatalogTest {
    private final PublicHttpClient http=mock(PublicHttpClient.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final MercadoLivreMonitor monitor=new MercadoLivreMonitor(http,new StructuredProductParser(mapper),TestSupport.config(),mapper,"test-token");
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
