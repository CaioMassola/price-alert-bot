package com.pricealert.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricealert.TestSupport;
import com.pricealert.domain.store.Store;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StructuredParserTest {
    @Test void handlesMetadataVariantsWithoutInferringAvailabilityOrCouponTerms() throws Exception {
        for(String image:List.of("[]","[\"https://image.test/a\"]","\"https://image.test/a\"","{}"))
            for(String availability:List.of("InStock","https://schema.org/LimitedAvailability","OutOfStock")) {
                var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(product("{}"));
                node.put("sku","explicit-id"); node.put("url",url); node.set("image",mapper.readTree(image));
                var offer=(com.fasterxml.jackson.databind.node.ObjectNode)node.path("offers").get(0);
                offer.put("availability",availability); node.set("offers",offer);
                var result=parser.parse(Store.KABUM,html(node.toString()),url).getFirst();
                assertThat(result.externalId()).isEqualTo("explicit-id");
                assertThat(result.available()).isEqualTo(!availability.equals("OutOfStock"));
            }
        for(String discount:List.of("{\"discountCode\":\"SALE\"}",
            "{\"discountCode\":\""+"A".repeat(201)+"\",\"discountAmount\":1}"))
            assertThat(parser.parse(Store.KABUM,html(product(discount)),url).getFirst().coupon()).isNull();
        assertThat(parser.parse(Store.KABUM,html(product("{\"discountCode\":\"SALE\",\"discountAmount\":1}")),url).getFirst().coupon().discountValue()).isEqualByComparingTo("1");
        assertThat(StructuredProductParser.money(null)).isNull();
        assertThat(StructuredProductParser.money(mapper.nullNode())).isNull();
        var emptyMapper=mock(ObjectMapper.class); when(emptyMapper.readTree(anyString())).thenReturn(null);
        assertThat(new StructuredProductParser(emptyMapper).parse(Store.KABUM,html("{}"),url)).isEmpty();
    }
    @Test void trackedPagesMatchCanonicalUrlAndRejectUnrelatedProducts() {
        var http=mock(PublicHttpClient.class);
        var monitor=new com.pricealert.monitor.kabum.KabumMonitor(http,parser,TestSupport.config(),mapper);
        when(http.get(eq(Store.KABUM),anyString(),isNull())).thenReturn(html(product("{}").replace("\"name\":", "\"sku\":\"different\",\"name\":")));
        assertThat(monitor.searchProducts(MonitorRequest.product(url))).hasSize(1);
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.product("https://www.kabum.com.br/produto/999")))
            .isInstanceOf(StoreAccessException.class);
    }
    @Test void discoveryStopsAtConfiguredLimitAndIgnoresNonproductLinks() {
        var http=mock(PublicHttpClient.class); var config=mock(com.pricealert.config.MonitorConfig.class);
        when(config.maxProducts()).thenReturn(1);
        var monitor=new com.pricealert.monitor.kabum.KabumMonitor(http,parser,config,mapper);
        when(http.get(eq(Store.KABUM),contains("/busca/"),isNull())).thenReturn("<a href='/help'>help</a><a href='/produto/123'>one</a><a href='/produto/456'>two</a>");
        when(http.get(Store.KABUM,url,null)).thenReturn(html(product("{}")));
        assertThat(monitor.searchProducts(MonitorRequest.search("keyboard"))).hasSize(1);
        verify(http,never()).get(eq(Store.KABUM),contains("456"),isNull());
    }
    final ObjectMapper mapper=new ObjectMapper();
    final StructuredProductParser parser=new StructuredProductParser(mapper);
    final String url="https://www.kabum.com.br/produto/123";
    String html(String json) { return "<script type='application/ld+json'>"+json+"</script>"; }
    String product(String discount) {
        return """
            {"@type":"Product","name":"Keyboard","url":"/produto/123#details",
            "image":{"url":"https://example.com/image.jpg"},"offers":[{"price":"100","priceCurrency":"BRL",
            "availability":"https://schema.org/InStock","priceSpecification":[{"priceType":"StrikethroughPrice","price":"200"}],
            "discount":%s}]}
            """.formatted(discount);
    }
    @Test void readsNestedMetadataAndExplicitCouponWithoutDuplicatingProducts() {
        String json=product("{\"discountCode\":\"SALE\",\"discountPercentage\":10,\"validThrough\":\"2099-01-01T00:00:00Z\"}");
        var result=parser.parse(Store.KABUM,html("[null,"+json+","+json+"]"),url);
        assertThat(result).hasSize(1); var item=result.getFirst();
        assertThat(item.externalId()).isEqualTo("123"); assertThat(item.originalPrice()).isEqualByComparingTo("200");
        assertThat(item.coupon().code()).isEqualTo("SALE"); assertThat(item.url()).isEqualTo(url);
    }
    @Test void ignoresMalformedMetadataAndUnpricedProducts() {
        assertThat(parser.parse(Store.KABUM,html("{")+html("")+html("{\"@type\":\"Product\",\"offers\":[]}"),url)).isEmpty();
        assertThat(parser.parse(Store.KABUM,html(product("{}").replace("BRL","USD")),url)).isEmpty();
        assertThat(StructuredProductParser.money(mapper.getNodeFactory().textNode("bad"))).isNull();
        assertThat(StructuredProductParser.brazilianMoney("indisponivel")).isNull();
        assertThatThrownBy(()->StructuredProductParser.identity(Store.KABUM,"https://www.kabum.com.br/search"))
            .isInstanceOf(StoreAccessException.class);
    }
    @Test void ignoresExpiredOrMalformedCouponExpiry() {
        for(String expiry:List.of("bad","2000-01-01T00:00:00Z")) {
            var result=parser.parse(Store.KABUM,html(product("{\"discountCode\":\"SALE\",\"discountAmount\":10,\"validThrough\":\""+expiry+"\"}")),url);
            assertThat(result.getFirst().coupon()).isNull();
        }
        assertThat(parser.parse(Store.KABUM,html(product("{}").replace("\"priceSpecification\":[{\"priceType\":\"StrikethroughPrice\",\"price\":\"200\"}]","\"priceSpecification\":{}")),url))
            .hasSize(1);
    }
    @Test void discoversProductLinksAndSkipsMissingProducts() {
        PublicHttpClient http=mock(PublicHttpClient.class);
        HtmlStoreMonitor monitor=new HtmlStoreMonitor(http,parser,TestSupport.config()) {
            public Store getStore() { return Store.KABUM; }
            protected String searchUrl(String query) { return "https://www.kabum.com.br/search?q="+encode(query); }
            protected boolean isProductUrl(String candidate) { return candidate.contains("/produto/"); }
        };
        when(http.get(Store.KABUM,"https://www.kabum.com.br/search?q=keyboard",null))
            .thenReturn("<a href='https://evil.test/produto/1'>bad</a><a href='/produto/999'>missing</a><a href='/produto/123'>ok</a>");
        when(http.get(Store.KABUM,"https://www.kabum.com.br/produto/999",null)).thenThrow(new StoreAccessException(404,"missing"));
        when(http.get(Store.KABUM,url,null)).thenReturn(html(product("{}")));
        assertThat(monitor.searchProducts(MonitorRequest.search("keyboard"))).hasSize(1);
        when(http.get(Store.KABUM,url,null)).thenReturn("<html></html>");
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("keyboard"))).isInstanceOf(StoreAccessException.class);
        when(http.get(Store.KABUM,url,null)).thenThrow(new StoreAccessException(403,"blocked"));
        assertThatThrownBy(()->monitor.searchProducts(MonitorRequest.search("keyboard")))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(403));
    }
}
