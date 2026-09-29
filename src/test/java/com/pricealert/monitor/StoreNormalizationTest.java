package com.pricealert.monitor;
import com.pricealert.TestSupport;
import com.pricealert.domain.store.Store;
import com.pricealert.monitor.amazon.AmazonMonitor;
import com.pricealert.monitor.kabum.KabumMonitor;

import com.pricealert.monitor.mercadolivre.MercadoLivreMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StoreNormalizationTest {
    @Test void htmlFallbackRecognizesStoreLinksAndInvalidKabumJson() {
        when(http.get(eq(Store.AMAZON),anyString(),isNull())).thenReturn("<a href='/dp/B012345678'>product</a>","<html></html>");
        assertThatThrownBy(()->new AmazonMonitor(http,parser,TestSupport.config()).searchProducts(MonitorRequest.search("phone")))
            .isInstanceOf(StoreAccessException.class);
        when(http.get(eq(Store.KABUM),anyString(),isNull())).thenReturn("<a href='/produto/123'>product</a>","<script id='__NEXT_DATA__'>{</script>");
        assertThatThrownBy(()->new KabumMonitor(http,parser,TestSupport.config(),mapper).searchProducts(MonitorRequest.search("keyboard")))
            .isInstanceOf(StoreAccessException.class);
    }
    @Test void couponLabelDoesNotInventDiscountTerms() {
        when(http.get(eq(Store.KABUM),anyString(),isNull())).thenReturn("""
            <script id='__NEXT_DATA__'>{"props":{"pageProps":{"code":123,"name":"Keyboard","price":100,
            "available":true,"stamps":{"title":"CUPOM SALE10"}}}}</script>
            """);
        var item=new KabumMonitor(http,parser,TestSupport.config(),mapper).searchProducts(MonitorRequest.search("keyboard")).getFirst();
        assertThat(item.coupon().code()).isEqualTo("SALE10"); assertThat(item.coupon().discountPercentage()).isNull();
    }
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final StructuredProductParser parser=new StructuredProductParser(mapper);
    private final PublicHttpClient http=mock(PublicHttpClient.class);
    private String fixture(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/fixtures/"+name)) { return new String(stream.readAllBytes(),StandardCharsets.UTF_8); }
    }
    @Test void normalizesRealSavedKabumPublicJson() throws Exception {
        when(http.get(eq(Store.KABUM),anyString(),isNull())).thenReturn(fixture("kabum-real.html"));
        var result=new KabumMonitor(http,parser,TestSupport.config(),mapper).searchProducts(MonitorRequest.search("teclado"));
        assertThat(result).hasSize(3); assertThat(result.getFirst().currentPrice()).isEqualByComparingTo("139.99");
        assertThat(result.getFirst().externalId()).isEqualTo("93160"); assertThat(result.getFirst().available()).isTrue();
        assertThat(result).allSatisfy(p->{ assertThat(p.name()).isNotBlank(); assertThat(p.url()).startsWith("https://www.kabum.com.br/produto/"); });
    }
    @Test void normalizesMercadoLivreApi() throws Exception {
        var monitor=new MercadoLivreMonitor(http,parser,TestSupport.config(),mapper,TestSupport.tokens("token-for-test"));
        var result=monitor.normalize(mapper.readTree(fixture("mercadolivre.json")));
        assertThat(result.currentPrice()).isEqualByComparingTo("599.90"); assertThat(result.available()).isTrue();
    }
    @Test void normalizesAmazonProductPage() throws Exception {
        when(http.get(eq(Store.AMAZON),anyString(),isNull())).thenReturn(fixture("amazon.html"));
        var result=new AmazonMonitor(http,parser,TestSupport.config()).searchProducts(MonitorRequest.product("https://www.amazon.com.br/dp/B012345678"));
        assertThat(result.getFirst().currentPrice()).isEqualByComparingTo("1199.90");
        assertThat(result.getFirst().originalPrice()).isEqualByComparingTo("1999.90");
    }
    @Test void schemaChangesFailVisibly() {
        when(http.get(eq(Store.AMAZON),anyString(),isNull())).thenReturn("<html>Loading</html>");
        assertThatThrownBy(()->new AmazonMonitor(http,parser,TestSupport.config())
            .searchProducts(MonitorRequest.product("https://www.amazon.com.br/dp/B012345678")))
            .isInstanceOf(StoreAccessException.class);
    }
    @Test void amazonErrorPageIsNotReportedAsParserFailure() {
        when(http.get(eq(Store.AMAZON),anyString(),isNull()))
            .thenReturn("<html><title>Amazon.com.br Algo deu errado</title></html>");
        assertThatThrownBy(()->new AmazonMonitor(http,parser,TestSupport.config())
            .searchProducts(MonitorRequest.search("teclado")))
            .isInstanceOfSatisfying(StoreAccessException.class,e->assertThat(e.status()).isEqualTo(503));
    }
    @Test void rejectsForeignUrlsBeforeRequest() {
        assertThatThrownBy(()->Store.KABUM.validateUrl("https://www.kabum.com.br.evil.test/produto/1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Store.KABUM.validateUrl("https://127.0.0.1/produto/1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Store.KABUM.validateUrl("https://user:pass@www.kabum.com.br/produto/1")).isInstanceOf(IllegalArgumentException.class);
    }
}

