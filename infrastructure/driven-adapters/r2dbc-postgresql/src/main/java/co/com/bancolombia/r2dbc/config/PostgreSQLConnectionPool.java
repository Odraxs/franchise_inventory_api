package co.com.bancolombia.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PostgreSQLConnectionPool {
    /* Change these values for your project */
    public static final int INITIAL_SIZE = 12;
    public static final int MAX_SIZE = 15;
    public static final int MAX_IDLE_TIME = 30;
    public static final int DEFAULT_PORT = 5432;
    private static final String POOL_NAME = "api-postgres-connection-pool";
    private static final String VALIDATION_QUERY = "SELECT 1";

	@Bean
	public ConnectionPool getConnectionConfig(PostgresqlConnectionProperties properties) {
        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(buildConnectionConfiguration(properties)))
                .name(POOL_NAME)
                .initialSize(INITIAL_SIZE)
                .maxSize(MAX_SIZE)
                .maxIdleTime(Duration.ofMinutes(MAX_IDLE_TIME))
                .validationQuery(VALIDATION_QUERY)
                .build();

		return new ConnectionPool(poolConfiguration);
	}

    PostgresqlConnectionConfiguration buildConnectionConfiguration(PostgresqlConnectionProperties properties) {
        return PostgresqlConnectionConfiguration.builder()
                .host(properties.host())
                .port(properties.port())
                .database(properties.database())
                .schema(properties.schema())
                .username(properties.username())
                .password(properties.password())
                .sslMode(toSslMode(properties.sslMode()))
                .build();
    }

    /**
     * Maps the {@code adapters.r2dbc.ssl-mode} value to the R2DBC enum. The property uses the
     * libpq/JDBC spelling ({@code disable}, {@code require}, {@code verify-full}, ...), so the same
     * value also feeds the {@code sslmode} parameter of the Flyway JDBC URL.
     */
    static SSLMode toSslMode(String sslMode) {
        return SSLMode.fromValue(sslMode);
    }
}
