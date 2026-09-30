package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;
public class V3__allow_zero_game_prices extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var names=new ArrayList<String>();
        try(var statement=context.getConnection().createStatement();
            var rows=statement.executeQuery("SELECT t.constraint_name FROM information_schema.table_constraints t JOIN information_schema.check_constraints c ON c.constraint_name=t.constraint_name AND c.constraint_schema=t.constraint_schema WHERE LOWER(t.table_name)='price_history' AND t.table_schema=CURRENT_SCHEMA() AND t.constraint_type='CHECK' AND LOWER(c.check_clause) LIKE '%price%>%0%'")) {
            while(rows.next()) names.add(rows.getString(1));
        }
        try(var statement=context.getConnection().createStatement()) {
            for(String name:names) statement.execute("ALTER TABLE price_history DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
            statement.execute("ALTER TABLE price_history ADD CONSTRAINT price_history_nonnegative CHECK(price >= 0)");
        }
    }
}
