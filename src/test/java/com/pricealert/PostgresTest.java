package com.pricealert;
import com.pricealert.repository.*;
import com.pricealert.service.ProductService;
import com.pricealert.monitor.kabum.KabumMonitor;
import com.pricealert.monitor.MonitorRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;
@EnabledIfSystemProperty(named="postgres",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "monitor.promotions-enabled=false","monitor.tracked-products.interval=PT24H",
    "discord.webhook-url=","debug=false"
})
@DirtiesContext
class PostgresTest {
    private static EmbeddedPostgres postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0).start();
        registry.add("spring.datasource.url",()->postgres.getJdbcUrl("postgres","postgres"));
        registry.add("spring.datasource.username",()->"postgres");
        registry.add("spring.datasource.password",()->"");
    }
    @Autowired ProductService service;
    @Autowired ProductRepository products;
    @Autowired PriceHistoryRepository history;
    @Autowired AlertRepository alerts;
    @Autowired KabumMonitor kabum;
    @Autowired TestRestTemplate rest;
    @AfterAll static void stop(@Autowired javax.sql.DataSource dataSource) throws Exception {
        if(dataSource instanceof com.zaxxer.hikari.HikariDataSource pool) pool.close();
        if(postgres!=null) postgres.close();
    }
    @Test void realPostgresMigrationAndRestApi() {
        service.accept(TestSupport.snapshot("70","200"),null,false);
        assertThat(products.findByStoreAndExternalId(com.pricealert.domain.store.Store.KABUM,"123")).isPresent();
        assertThat(history.count()).isPositive(); assertThat(alerts.count()).isPositive();
        assertThat(rest.getForEntity("/actuator/health",String.class).getBody()).contains("UP");
        assertThat(rest.getForEntity("/api/products",String.class).getBody()).contains("Produto teste");
    }
    @Test @EnabledIfSystemProperty(named="live",matches="true")
    void realKabumHttpToPostgres() {
        var snapshots=kabum.searchProducts(MonitorRequest.search("teclado"));
        assertThat(snapshots).isNotEmpty();
        for(var snapshot:snapshots) {
            service.accept(snapshot,null,false);
            var stored=products.findByStoreAndExternalId(snapshot.store(),snapshot.externalId()).orElseThrow();
            assertThat(stored.currentPrice).isEqualByComparingTo(snapshot.currentPrice());
            assertThat(history.findByProductIdOrderByCollectedAtAsc(stored.id)).isNotEmpty();
        }
    }
}
