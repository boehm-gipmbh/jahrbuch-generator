package de.jamsintown;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
@IfBuildProfile("local")
public class DbMigrationService {

    private static final Logger LOG = Logger.getLogger(DbMigrationService.class);

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent ev) throws Exception {
        LOG.info("=== DB Migration ===");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version VARCHAR(100) PRIMARY KEY,
                        applied_at TIMESTAMP DEFAULT NOW()
                    )""");

            stmt.execute("""
                    INSERT INTO schema_migrations (version)
                    SELECT 'V1__initial_schema'
                    WHERE EXISTS (
                        SELECT 1 FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'bilder'
                    )
                    AND NOT EXISTS (
                        SELECT 1 FROM schema_migrations WHERE version = 'V1__initial_schema'
                    )""");

            for (URL url : findMigrations()) {
                String filename = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
                String version = filename.replace(".sql", "");

                ResultSet rs = stmt.executeQuery(
                        "SELECT 1 FROM schema_migrations WHERE version = '" + version + "'");
                if (rs.next()) {
                    LOG.infof("[skip] %s", version);
                    continue;
                }

                LOG.infof("[run]  %s", version);
                String sql = new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
                for (String part : sql.split(";")) {
                    String trimmed = part.strip();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
                stmt.execute("INSERT INTO schema_migrations (version) VALUES ('" + version + "')");
                LOG.infof("[done] %s", version);
            }
        }
        LOG.info("=== Migration abgeschlossen ===");
    }

    private List<URL> findMigrations() throws Exception {
        List<URL> result = new ArrayList<>();
        var resources = Thread.currentThread().getContextClassLoader()
                .getResources("db/migration");
        while (resources.hasMoreElements()) {
            URL dir = resources.nextElement();
            try (InputStream is = dir.openStream()) {
                String listing = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : listing.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("V") && line.endsWith(".sql")) {
                        result.add(new URL(dir + "/" + line));
                    }
                }
            }
        }
        Collections.sort(result, (a, b) -> a.getPath().compareTo(b.getPath()));
        return result;
    }
}
