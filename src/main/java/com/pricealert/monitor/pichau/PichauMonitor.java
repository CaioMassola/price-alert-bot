package com.pricealert.monitor.pichau;
import com.pricealert.monitor.*;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import org.springframework.stereotype.Component;
@Component
public class PichauMonitor extends HtmlStoreMonitor {
    public PichauMonitor(PublicHttpClient http, StructuredProductParser parser, MonitorConfig config) { super(http,parser,config); }
    public Store getStore() { return Store.PICHAU; }
    protected String searchUrl(String query) { return "https://www.pichau.com.br/search?q="+encode(query); }
    protected boolean isProductUrl(String url) {
        String path=java.net.URI.create(url).getPath();
        return path.split("/").length==2 && path.length()>30 && path.contains("-");
    }
}

