package com.pricealert.monitor.mercadolivre;
import com.pricealert.monitor.*;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.product.ProductSnapshot;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.Instant;
@Component
public class MercadoLivreMonitor extends HtmlStoreMonitor {
    private final String token;
    private final ObjectMapper mapper;
    public MercadoLivreMonitor(PublicHttpClient http, StructuredProductParser parser, MonitorConfig config,
        ObjectMapper mapper,@Value("${mercadolivre.access-token:}") String token) {
        super(http,parser,config); this.token=token; this.mapper=mapper;
    }
    public Store getStore() { return Store.MERCADO_LIVRE; }
    protected String searchUrl(String query) { return "https://lista.mercadolivre.com.br/"+encode(query).replace("+","-"); }
    protected boolean isProductUrl(String url) { return url.contains("produto.mercadolivre.com.br/MLB-") || url.matches(".*mercadolivre.com.br/.*/p/MLB[0-9]+"); }
    @Override public List<ProductSnapshot> searchProducts(MonitorRequest request) {
        if(token.isBlank()) return super.searchProducts(request);
        try {
            List<ProductSnapshot> result=new ArrayList<>();
            if(request.productUrl()!=null) {
                var uri=getStore().validateUrl(request.productUrl());
                var match=java.util.regex.Pattern.compile("/p/(MLB[0-9]+)(?:/|$)").matcher(uri.getPath());
                if(match.find()) {
                    var selected=java.util.regex.Pattern.compile("(?:^|&)wid=(MLB[0-9]+)(?:&|$)")
                        .matcher(uri.getRawQuery()==null?"":uri.getRawQuery());
                    String itemId=selected.find()?selected.group(1):null;
                    for(var offer:catalogOffers(match.group(1),config.maxProducts()))
                        if(itemId==null || itemId.equals(offer.externalId())) result.add(offer);
                }
                else result.add(normalize(read("/items/"+StructuredProductParser.identity(getStore(),request.productUrl()))));
            } else {
                JsonNode root=read("/products/search?status=active&site_id=MLB&q="+encode(request.query())+"&limit="+config.maxProducts());
                if(!root.path("results").isArray()) throw new StoreAccessException(422,"Catalogo sem resultados reconheciveis");
                Set<String> seen=new HashSet<>();
                int checked=0;
                for(JsonNode product:root.path("results")) {
                    if(checked++>=config.maxProducts() || result.size()>=config.maxProducts()) break;
                    for(var offer:catalogOffers(product.path("id").asText(),config.maxProducts()-result.size()))
                        if(seen.add(offer.externalId())) result.add(offer);
                }
            }
            if(result.isEmpty()) throw new StoreAccessException(422,"API nao retornou produtos validos");
            return result;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new StoreAccessException(422,"JSON invalido da API"); }
    }
    private JsonNode read(String path) throws com.fasterxml.jackson.core.JsonProcessingException {
        return mapper.readTree(http.get(getStore(),"https://api.mercadolibre.com"+path,token));
    }
    private List<ProductSnapshot> catalogOffers(String id,int limit) throws com.fasterxml.jackson.core.JsonProcessingException {
        if(!id.matches("MLB[0-9]+")) throw new StoreAccessException(422,"Identificador de catalogo invalido");
        JsonNode product=read("/products/"+id);
        if(!"active".equals(product.path("status").asText())) return List.of();
        String name=product.path("name").asText();
        String url=product.path("permalink").asText();
        if(url.isBlank()) url="https://www.mercadolivre.com.br/p/"+id;
        getStore().validateUrl(url);
        JsonNode offers;
        try { offers=read("/products/"+id+"/items?limit="+limit); }
        catch(StoreAccessException e) {
            if(e.status()==404) return List.of(); // Catalog entry exists but has no listing resource.
            throw e;
        }
        if(!offers.path("results").isArray()) throw new StoreAccessException(422,"Ofertas de catalogo invalidas");
        List<ProductSnapshot> result=new ArrayList<>();
        for(JsonNode item:offers.path("results")) {
            if(result.size()>=limit) break;
            String itemId=item.path("item_id").asText();
            if(!itemId.matches("MLB[0-9]+") || !"BRL".equals(item.path("currency_id").asText()) ||
                !"new".equals(item.path("condition").asText()) || item.path("min_purchase_unit").asInt(1)>1) continue;
            // This endpoint lists competing offers. Bind the link and history to the specific seller item.
            String base=url.split("\\?",2)[0];
            result.add(new ProductSnapshot(itemId,name,base+"?wid="+itemId,
                product.path("pictures").path(0).path("url").asText(null),
                StructuredProductParser.money(item.path("price")),StructuredProductParser.money(item.path("original_price")),
                getStore(),true,null,Instant.now()));
        }
        return result;
    }
    public ProductSnapshot normalize(JsonNode item) {
        if(!"BRL".equals(item.path("currency_id").asText())) throw new StoreAccessException(422,"Moeda nao suportada");
        return new ProductSnapshot(item.path("id").asText(),item.path("title").asText(),item.path("permalink").asText(),
            item.path("secure_thumbnail").asText(item.path("thumbnail").asText(null)),StructuredProductParser.money(item.path("price")),
            StructuredProductParser.money(item.path("original_price")),getStore(),
            item.path("available_quantity").asInt()>0 && !"closed".equals(item.path("status").asText()),null,Instant.now());
    }
}

