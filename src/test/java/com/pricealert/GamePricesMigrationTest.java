package com.pricealert;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.api.migration.Context;
import db.migration.V3__allow_zero_game_prices;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GamePricesMigrationTest {
    @Test void preservesExistingPricesAndOtherConstraintsWhileAllowingZero() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:gameprices;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE price_history(price NUMERIC(19,2) NOT NULL CHECK(price>0), product_id BIGINT CHECK(product_id>0))");
        jdbc.update("INSERT INTO price_history VALUES(100,1)");
        try(var connection=source.getConnection()) {
            var context=mock(Context.class); when(context.getConnection()).thenReturn(connection);
            new V3__allow_zero_game_prices().migrate(context);
        }
        jdbc.update("INSERT INTO price_history VALUES(0,2)");
        assertThat(jdbc.queryForObject("SELECT SUM(price) FROM price_history",java.math.BigDecimal.class)).isEqualByComparingTo("100");
        for(String values:new String[]{"-1,3","NULL,3","1,0"})
            assertThatThrownBy(()->jdbc.update("INSERT INTO price_history VALUES("+values+")"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
