package com.pricealert;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.product.ProductSnapshot;
import com.pricealert.domain.store.Store;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DomainBoundaryTest {
    private ProductSnapshot snapshot(String id,String name,String url,String image,BigDecimal price,BigDecimal original,Store store,Instant at) {
        return new ProductSnapshot(id,name,url,image,price,original,store,true,null,at);
    }
    @Test void rejectsMissingIdentityPriceAndOversizedFields() {
        Object[][] invalid={{null,"Name","https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {" ","Name","https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1",null,"https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1"," ","https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1","Name","https://www.kabum.com.br",null,Store.KABUM,Instant.EPOCH},
            {"1","Name","https://www.kabum.com.br",BigDecimal.ZERO,Store.KABUM,Instant.EPOCH},
            {"1","Name","https://www.kabum.com.br",BigDecimal.ONE,null,Instant.EPOCH},
            {"1","Name","https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,null},
            {"x".repeat(201),"Name","https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1","x".repeat(1001),"https://www.kabum.com.br",BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1","Name",null,BigDecimal.ONE,Store.KABUM,Instant.EPOCH},
            {"1","Name","https://www.kabum.com.br/"+"x".repeat(2048),BigDecimal.ONE,Store.KABUM,Instant.EPOCH}};
        for(var row:invalid) assertThatThrownBy(()->snapshot((String)row[0],(String)row[1],(String)row[2],null,
            (BigDecimal)row[3],null,(Store)row[4],(Instant)row[5])).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void sanitizesImagesAndReferencePrices() {
        for(String image:Arrays.asList(null,"http://image.test/a","https://image.test/"+"a".repeat(2048),"https://image.test/a")) {
            for(BigDecimal original:Arrays.asList(null,BigDecimal.ZERO,BigDecimal.TEN)) {
                var result=snapshot("1","Name","https://www.kabum.com.br",image,new BigDecimal("1.235"),original,Store.KABUM,Instant.EPOCH);
                assertThat(result.currentPrice()).isEqualByComparingTo("1.24");
                assertThat(result.imageUrl()).isEqualTo(image!=null && image.startsWith("https://") && image.length()<=2048?image:null);
                assertThat(result.originalPrice()).isEqualTo(original!=null && original.signum()>0?original:null);
            }
        }
    }
    @Test void onlyAllowsTrustedHttpsWithoutCredentialsFragmentsOrCustomPorts() {
        for(String url:Arrays.asList(null,"broken url","http://www.kabum.com.br","https://foreign.test","https://user@www.kabum.com.br",
            "https://www.kabum.com.br:444","https://www.kabum.com.br/#fragment"))
            assertThatThrownBy(()->Store.KABUM.validateUrl(url)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Store.KABUM.validateUrl("https://www.kabum.com.br:443").getPort()).isEqualTo(443);
        assertThat(Store.KABUM.validateUrl("https://www.kabum.com.br").getHost()).isEqualTo("www.kabum.com.br");
    }
    @Test void timingValidationRejectsEachMissingOrNonpositiveInterval() {
        var base=TestSupport.config();
        assertThat(base.isTimingValid()).isTrue();
        for(int index=0;index<4;index++) for(Duration invalid:Arrays.asList(null,Duration.ofSeconds(-1),Duration.ZERO)) {
            Duration[] durations={Duration.ZERO,Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(1)};
            durations[index]=invalid;
            var config=new MonitorConfig(durations[0],durations[1],durations[2],durations[3],base.minStoreDiscount(),base.minHistoricalDiscount(),base.trackedDrop(),3,5,true,List.of("test"));
            assertThat(config.isTimingValid()).isEqualTo(index==0 && Duration.ZERO.equals(invalid));
        }
    }
}
