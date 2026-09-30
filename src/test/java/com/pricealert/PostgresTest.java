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
    "mercadolivre.refresh-token=","mercadolivre.access-token=","mercadolivre.expires-at=",
    "mercadolivre.client-id=","mercadolivre.token-file=target/test-oauth-unused.json",
    "monitor.promotions-enabled=false","monitor.tracked-products.interval=PT24H",
    "discord.webhook-url=","discord.webhook-games=","debug=false"
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
        var free=new com.pricealert.domain.product.ProductSnapshot("free-game","Temporary giveaway",
            "https://store.epicgames.com/pt-BR/p/free-game",null,java.math.BigDecimal.ZERO,java.math.BigDecimal.TEN,
            com.pricealert.domain.store.Store.EPIC,true,null,java.time.Instant.now());
        service.accept(free,null,false);
        var game=products.findByStoreAndExternalId(free.store(),free.externalId()).orElseThrow();
        assertThat(game.currentPrice).isZero(); assertThat(game.storeDiscount).isEqualByComparingTo("100");
        assertThat(history.findByProductIdOrderByCollectedAtAsc(game.id)).hasSize(1);
    }
    @Test void homepageAndSwaggerDocumentApplicationEndpoints() throws Exception {
        var home=rest.getForEntity("/",String.class);
        assertThat(home.getStatusCode().value()).isEqualTo(200);
        assertThat(home.getBody()).contains("Documentação","/documentation.html","/health.html");
        assertThat(rest.getForEntity("/documentation.html",String.class).getBody()).contains("Voltar ao início","/swagger-ui/index.html");
        assertThat(rest.getForEntity("/health.html",String.class).getBody()).contains("Saúde da API","Voltar ao início","/health.js");
        assertThat(rest.getForEntity("/health.js",String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity("/swagger-ui/index.html",String.class).getBody()).contains("Swagger UI");
        var response=rest.getForEntity("/v3/api-docs",String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var spec=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
        assertThat(spec.path("info").path("title").asText()).contains("Milizé");
        assertThat(spec.path("paths").has("/api/products")).isTrue();
        assertThat(spec.path("paths").path("/api/tracked-products").has("post")).isTrue();
        assertThat(spec.path("paths").has("/actuator/health")).isFalse();
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
