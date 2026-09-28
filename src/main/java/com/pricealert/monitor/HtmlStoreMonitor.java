package com.pricealert.monitor;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.config.MonitorConfig;
import org.jsoup.Jsoup;
import java.util.*;
public abstract class HtmlStoreMonitor implements StoreMonitor {
    protected final PublicHttpClient http;
    protected final StructuredProductParser parser;
    protected final MonitorConfig config;
    protected HtmlStoreMonitor(PublicHttpClient http, StructuredProductParser parser, MonitorConfig config) {
        this.http=http; this.parser=parser; this.config=config;
    }
    protected abstract String searchUrl(String query);
    protected abstract boolean isProductUrl(String url);
    protected List<ProductSnapshot> parsePage(String html,String url) { return parser.parse(getStore(),html,url); }
    @Override public List<ProductSnapshot> searchProducts(MonitorRequest request) {
        String url=request.productUrl()!=null?request.productUrl():searchUrl(request.query());
        String html=http.get(getStore(),url,null);
        List<ProductSnapshot> direct=parsePage(html,url);
        if(request.productUrl()!=null) {
            if(direct.isEmpty()) throw new StoreAccessException(422,"Estrutura de produto nao reconhecida; nenhum preco armazenado");
            String expected=StructuredProductParser.identity(getStore(),url);
            return direct.stream().filter(p->p.externalId().equals(expected) || p.url().split("\\?")[0].equals(url.split("\\?")[0]))
                .findFirst().map(List::of).orElseThrow(()->new StoreAccessException(422,"Produto solicitado nao encontrado na resposta"));
        }
        if(!direct.isEmpty()) return direct.stream().limit(config.maxProducts()).toList();
        Set<String> links=new LinkedHashSet<>();
        for(var link:Jsoup.parse(html,url).select("a[href]")) {
            String candidate=link.absUrl("href").split("[?#]")[0];
            try { getStore().validateUrl(candidate); if(isProductUrl(candidate)) links.add(candidate); }
            catch(IllegalArgumentException ignored) { }
            if(links.size()>=config.maxProducts()) break;
        }
        if(links.isEmpty()) throw new StoreAccessException(422,"Busca sem produtos reconheciveis; acesso ou parser requer verificacao");
        List<ProductSnapshot> result=new ArrayList<>();
        for(String link:links) {
            try { result.addAll(searchProducts(MonitorRequest.product(link))); }
            catch(StoreAccessException e) { if(e.status()!=404 && e.status()!=422) throw e; }
        }
        if(result.isEmpty()) throw new StoreAccessException(422,"Nenhum produto valido nas paginas coletadas");
        return result;
    }
    protected String encode(String value) { return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8); }
}

