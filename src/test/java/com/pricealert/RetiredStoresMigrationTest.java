package com.pricealert;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
class RetiredStoresMigrationTest {
    @Test void removesRetiredStoreAndDependentsWhilePreservingSupportedStore() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:retired;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__initial_schema.sql")).execute(source);
        var jdbc=new JdbcTemplate(source);
        for(String store:new String[]{"RETIRED","KABUM"}) {
            jdbc.update("INSERT INTO products(store,external_id,name,url,active,available,deal) VALUES (?, '123', 'test', 'https://example.test', true,true,true)",store);
            long id=jdbc.queryForObject("SELECT id FROM products WHERE store=?",Long.class,store);
            jdbc.update("INSERT INTO price_history(product_id,price,available,collected_at) VALUES (?,100,true,CURRENT_TIMESTAMP)",id);
            jdbc.update("INSERT INTO alerts(product_id,alert_type,fingerprint,status,payload,attempts) VALUES (?,'STORE_DISCOUNT',?,'PENDING','{}',0)",id,store);
            jdbc.update("INSERT INTO coupons(product_id,code) VALUES (?,'SALE')",id);
            jdbc.update("INSERT INTO tracked_products(store,url,active) VALUES (?,'https://example.test',true)",store);
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__remove_retired_stores.sql")).execute(source);
        assertThat(jdbc.queryForList("SELECT store FROM products",String.class)).containsExactly("KABUM");
        assertThat(jdbc.queryForList("SELECT store FROM tracked_products",String.class)).containsExactly("KABUM");
        for(String table:new String[]{"price_history","alerts","coupons"})
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).isEqualTo(1L);
    }
}
