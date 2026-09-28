package com.pricealert.monitor.amazon;
import com.pricealert.monitor.*;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.product.ProductSnapshot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.Instant;
@Component
public class AmazonMonitor extends HtmlStoreMonitor {
    public AmazonMonitor(PublicHttpClient http, StructuredProductParser parser, MonitorConfig config) { super(http,parser,config); }
    public Store getStore() { return Store.AMAZON; }
    protected String searchUrl(String query) { return "https://www.amazon.com.br/s?k="+encode(query); }
    protected boolean isProductUrl(String url) { return url.matches(".*/(?:dp|gp/product)/[A-Z0-9]{10}.*"); }
    @Override protected List<ProductSnapshot> parsePage(String html, String url) {
        var document=Jsoup.parse(html,url);
        String pageTitle=document.title().toLowerCase(Locale.ROOT);
        if(pageTitle.contains("algo deu errado") || pageTitle.contains("sorry! something went wrong"))
            throw new StoreAccessException(503,"Amazon retornou uma pagina de erro, nao uma pagina de produtos");
        Element title=document.selectFirst("#productTitle");
        if(title==null) return super.parsePage(html,url);
        Element price=document.selectFirst("#corePriceDisplay_desktop_feature_div .a-price:not(.a-text-price) .a-offscreen, #corePrice_feature_div .a-price:not(.a-text-price) .a-offscreen");
        if(price==null) throw new StoreAccessException(422,"Amazon sem preco publico confirmado");
        Element original=document.selectFirst("#corePriceDisplay_desktop_feature_div .a-text-price .a-offscreen");
        Element image=document.selectFirst("#landingImage");
        boolean available=document.selectFirst("#add-to-cart-button, #buy-now-button")!=null;
        return List.of(new ProductSnapshot(StructuredProductParser.identity(getStore(),url),title.text(),
            "https://www.amazon.com.br/dp/"+StructuredProductParser.identity(getStore(),url),
            image==null?null:image.attr("src"),StructuredProductParser.brazilianMoney(price.text()),
            original==null?null:StructuredProductParser.brazilianMoney(original.text()),getStore(),available,null,Instant.now()));
    }
}

