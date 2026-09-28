import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Creates the local database without putting credentials on the command line. */
class PrepareDatabase {
    public static void main(String[] args) {
        try {
            Map<String,String> env=new HashMap<>();
            for(String line:Files.readAllLines(Path.of(".env"))) {
                int separator=line.indexOf('=');
                if(separator>0 && !line.stripLeading().startsWith("#"))
                    env.put(line.substring(0,separator).trim(),line.substring(separator+1).trim());
            }
            String url=env.get("DATABASE_URL");
            Matcher match=Pattern.compile("^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):([0-9]+)/([A-Za-z_][A-Za-z0-9_]*)$").matcher(url);
            if(!match.matches()) throw new IllegalArgumentException("Local database URL required");
            String database=match.group(2);
            Class.forName("org.postgresql.Driver");
            try(Connection connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+match.group(1)+"/postgres",
                    env.get("DATABASE_USERNAME"),env.get("DATABASE_PASSWORD"))) {
                boolean exists;
                try(PreparedStatement query=connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                    query.setString(1,database);
                    try(ResultSet result=query.executeQuery()) { exists=result.next(); }
                }
                if(!exists) try(Statement create=connection.createStatement()) { create.executeUpdate("CREATE DATABASE \""+database+"\""); }
            }
            System.out.println("Banco local pronto.");
        } catch(Exception error) {
            System.err.println("Falha ao preparar banco local: "+error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
