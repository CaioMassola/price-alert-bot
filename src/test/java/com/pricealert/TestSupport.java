package com.pricealert;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.domain.price.PriceHistory;
import com.pricealert.domain.store.Store;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public class TestSupport {
    public static MonitorConfig config() {
        return new MonitorConfig(Duration.ZERO,Duration.ofSeconds(2),Duration.ofHours(6),Duration.ofDays(7),
            new BigDecimal("40"),new BigDecimal("20"),new BigDecimal("10"),3,5,true,List.of("teclado"));
    }
    public static ProductSnapshot snapshot(String price,String original) {
        return new ProductSnapshot("123","Produto teste","https://www.kabum.com.br/produto/123",null,
            new BigDecimal(price),original==null?null:new BigDecimal(original),Store.KABUM,true,null,Instant.now());
    }
    public static List<PriceHistory> history(String... prices) {
        var result=new ArrayList<PriceHistory>();
        for(int i=0;i<prices.length;i++) {
            var row=new PriceHistory(); row.price=new BigDecimal(prices[i]); row.collectedAt=Instant.now().minus(Duration.ofDays(10-i));
            result.add(row);
        }
        return result;
    }
}

