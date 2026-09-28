package com.pricealert.monitor.kabum;
import com.pricealert.monitor.*;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import org.springframework.stereotype.Component;
import com.pricealert.domain.product.ProductSnapshot;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import com.pricealert.domain.coupon.Coupon;
import org.jsoup.Jsoup;
@Component
public class KabumMonitor extends HtmlStoreMonitor {
    private final ObjectMapper mapper;
    public KabumMonitor(PublicHttpClient http, StructuredProductParser parser, MonitorConfig config,ObjectMapper mapper) {
        super(http,parser,config); this.mapper=mapper;
    }
    public Store getStore() { return Store.KABUM; }
    protected String searchUrl(String query) { return "https://www.kabum.com.br/busca/"+encode(query).replace("+","-"); }
    protected boolean isProductUrl(String url) { return url.contains("/produto/"); }
    @Override protected List<ProductSnapshot> parsePage(String html,String url) {
        var script=Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if(script==null) return super.parsePage(html,url);
        try {
            JsonNode props=mapper.readTree(script.data()).path("props").path("pageProps");
            List<ProductSnapshot> products=new ArrayList<>();
            collect(props,products);
            return products.isEmpty()?super.parsePage(html,url):products.stream()
                .collect(java.util.stream.Collectors.toMap(ProductSnapshot::externalId,p->p,(a,b)->a,LinkedHashMap::new))
                .values().stream().toList();
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new StoreAccessException(422,"KaBuM retornou JSON invalido");
        }
    }
    private void collect(JsonNode node,List<ProductSnapshot> products) {
        if(node.isObject() && node.has("code") && node.has("name") && node.has("price") && node.has("available")) {
            String id=node.path("code").asText();
            BigDecimal cash=StructuredProductParser.money(node.path("priceWithDiscount"));
            BigDecimal price=cash!=null && cash.signum()>0?cash:StructuredProductParser.money(node.path("price"));
            if(price==null || price.signum()<=0) return;
            boolean exclusive=node.path("offer").path("isLoggedUserExclusive").asBoolean() ||
                node.path("offer").path("isPrimeExclusive").asBoolean();
            if(exclusive) return;
            products.add(new ProductSnapshot(id,node.path("name").asText(),"https://www.kabum.com.br/produto/"+id,
                node.path("image").asText(null),price,StructuredProductParser.money(node.path("oldPrice")),
                getStore(),node.path("available").asBoolean(),coupon(node,id),Instant.now()));
            return;
        }
        if(node.isContainerNode()) node.forEach(child->collect(child,products));
    }
    private Coupon coupon(JsonNode product,String id) {
        String title=product.path("stamps").path("title").asText();
        var match=java.util.regex.Pattern.compile("(?i)^CUPOM\\s+([A-Z0-9_-]{3,40})$").matcher(title);
        if(!match.matches()) return null;
        // A code's name is not proof of its percentage, conditions, or expiration.
        return new Coupon(match.group(1),null,null,null,null,"https://www.kabum.com.br/produto/"+id);
    }
}

