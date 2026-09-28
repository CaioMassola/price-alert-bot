package com.pricealert;
import com.pricealert.service.ProductService;
import com.pricealert.repository.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "monitor.promotions-enabled=false","monitor.tracked-products.interval=PT24H","discord.webhook-url=","debug=false"
})
@DirtiesContext
class PipelineTest {
    @Autowired ProductService service;
    @Autowired ProductRepository products;
    @Autowired PriceHistoryRepository history;
    @Autowired AlertRepository alerts;
    @Test void persistsProductHistoryAndDeduplicatedOutboxInOnePipeline() {
        service.accept(TestSupport.snapshot("100","200"),new BigDecimal("100"),true);
        service.accept(TestSupport.snapshot("100","200"),new BigDecimal("100"),true);
        assertThat(products.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
        assertThat(alerts.count()).isEqualTo(1);
        service.accept(TestSupport.snapshot("90","200"),new BigDecimal("100"),true);
        assertThat(history.count()).isEqualTo(2); assertThat(alerts.count()).isEqualTo(2);
        var product=products.findAll().getFirst();
        assertThat(product.lowestPrice).isEqualByComparingTo("90");
        assertThat(product.previousPrice).isEqualByComparingTo("100");
        assertThat(alerts.findAll()).allSatisfy(a->assertThat(a.status).isEqualTo("PENDING"));
    }
}

