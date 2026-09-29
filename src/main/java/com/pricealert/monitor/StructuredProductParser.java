package com.pricealert.monitor;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.domain.store.Store;
import com.pricealert.domain.coupon.Coupon;
import com.fasterxml.jackson.databind.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Component
public class StructuredProductParser {
    private final ObjectMapper mapper;
    public StructuredProductParser(ObjectMapper mapper) { this.mapper=mapper; }
    public List<ProductSnapshot> parse(Store store, String html, String pageUrl) {
        var document=Jsoup.parse(html,pageUrl);
        List<ProductSnapshot> products=new ArrayList<>();
        for(var script:document.select("script[type=application/ld+json]")) {
            try { collect(mapper.readTree(script.data()),store,pageUrl,products); }
            catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Some pages include unrelated invalid metadata. */ }
        }
        return products.stream().collect(java.util.stream.Collectors.toMap(ProductSnapshot::externalId,p->p,(a,b)->a,LinkedHashMap::new)).values().stream().toList();
    }
    private void collect(JsonNode node, Store store, String pageUrl, List<ProductSnapshot> products) {
        if(node==null) return;
        if(node.isArray()) { node.forEach(n->collect(n,store,pageUrl,products)); return; }
        if(!node.isObject()) return;
        if(node.path("@type").asText().equals("Product")) {
            ProductSnapshot product=product(node,store,pageUrl);
            if(product!=null) products.add(product);
        }
        node.fields().forEachRemaining(e->{ if(e.getValue().isContainerNode()) collect(e.getValue(),store,pageUrl,products); });
    }
    private ProductSnapshot product(JsonNode node, Store store, String pageUrl) {
        JsonNode offer=node.path("offers");
        if(offer.isArray()) offer=offer.isEmpty()?mapper.createObjectNode():offer.get(0);
        // Aggregate lowPrice can represent a different variant; never treat it as a concrete product price.
        BigDecimal price=money(offer.path("price"));
        if(price==null) return null;
        if(!"BRL".equalsIgnoreCase(offer.path("priceCurrency").asText())) return null;
        String url=node.path("url").asText(offer.path("url").asText(pageUrl));
        if(url.startsWith("/")) url=java.net.URI.create(pageUrl).resolve(url).toString();
        int fragment=url.indexOf('#'); if(fragment>=0) url=url.substring(0,fragment);
        String id=node.path("sku").asText(node.path("productID").asText());
        if(id.isBlank()) id=identity(store,url);
        JsonNode image=node.path("image");
        String imageUrl=image.isArray() && !image.isEmpty()?image.get(0).asText():image.isTextual()?image.asText():image.path("url").asText(null);
        String availability=offer.path("availability").asText();
        boolean available=availability.endsWith("/InStock") || availability.equals("InStock") || availability.endsWith("/LimitedAvailability");
        BigDecimal reference=null;
        for(JsonNode specification:iterable(offer.path("priceSpecification"))) {
            if(specification.path("priceType").asText().endsWith("StrikethroughPrice")) reference=money(specification.path("price"));
        }
        return new ProductSnapshot(id,node.path("name").asText(),url,imageUrl,price,reference,store,available,publicCoupon(offer,url),Instant.now());
    }
    private List<JsonNode> iterable(JsonNode node) {
        if(node.isArray()) { List<JsonNode> nodes=new ArrayList<>(); node.forEach(nodes::add); return nodes; }
        return List.of(node);
    }
    private Coupon publicCoupon(JsonNode offer,String source) {
        // Only explicit machine-readable codes with declared monetary terms; no inferred banner codes.
        JsonNode discount=offer.path("discount");
        String code=discount.path("discountCode").asText();
        BigDecimal percentage=money(discount.path("discountPercentage")), value=money(discount.path("discountAmount"));
        if(code.isBlank() || code.length()>200 || percentage==null && value==null) return null;
        Instant expires=null;
        try { if(discount.hasNonNull("validThrough")) expires=Instant.parse(discount.path("validThrough").asText()); }
        catch(java.time.format.DateTimeParseException e) { return null; }
        if(expires!=null && !expires.isAfter(Instant.now())) return null;
        return new Coupon(code,percentage,value,money(discount.path("minimumPurchase")),expires,source);
    }
    public static String identity(Store store,String url) {
        String pattern=switch(store) {
            case MERCADO_LIVRE -> "(MLB-?[0-9]+)";
            case AMAZON -> "/(?:dp|gp/product)/([A-Z0-9]{10})";
            case KABUM -> "/produto/([0-9]+)";
        };
        var match=java.util.regex.Pattern.compile(pattern).matcher(url);
        if(!match.find()) throw new StoreAccessException(422,"Produto sem identificador reconhecivel");
        return match.group(1).replace("MLB-","MLB");
    }
    public static BigDecimal money(JsonNode node) {
        if(node==null || node.isMissingNode() || node.isNull()) return null;
        try { return new BigDecimal(node.asText()); } catch(NumberFormatException e) { return null; }
    }
    public static BigDecimal brazilianMoney(String text) {
        String clean=text.replaceAll("[^0-9,.]","");
        if(clean.contains(",")) clean=clean.replace(".","").replace(",",".");
        try { return new BigDecimal(clean); } catch(NumberFormatException e) { return null; }
    }
}

