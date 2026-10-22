package co.com.bancolombia.r2dbc.config;

// TODO: Load properties from the application.yaml file or from secrets manager
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings under {@code adapters.r2dbc}. {@code sslMode} ({@code ssl-mode} in YAML) takes the
 * libpq/JDBC spelling ({@code disable}, {@code require}, ...) and applies to both R2DBC and Flyway.
 */
@ConfigurationProperties(prefix = "adapters.r2dbc")
public record PostgresqlConnectionProperties(
        String host,
        Integer port,
        String database,
        String schema,
        String username,
        String password,
        String sslMode) {
}
