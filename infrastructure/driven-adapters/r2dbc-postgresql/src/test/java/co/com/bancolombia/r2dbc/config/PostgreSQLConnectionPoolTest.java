package co.com.bancolombia.r2dbc.config;

import io.r2dbc.postgresql.client.SSLMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.r2dbc.postgresql.PostgresqlConnectionConfigurationInspector.sslModeOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgreSQLConnectionPoolTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5432;
    private static final String DATABASE = "dbName";
    private static final String SCHEMA = "schema";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String DISABLE = "disable";
    private static final String UNKNOWN_SSL_MODE = "sometimes";

    private final PostgreSQLConnectionPool connectionPool = new PostgreSQLConnectionPool();

    private static PostgresqlConnectionProperties propertiesWithSslMode(String sslMode) {
        return new PostgresqlConnectionProperties(HOST, PORT, DATABASE, SCHEMA, USERNAME, PASSWORD, sslMode);
    }

    @Test
    void getConnectionConfigSuccess() {
        assertNotNull(connectionPool.getConnectionConfig(propertiesWithSslMode(DISABLE)));
    }

    @ParameterizedTest
    @CsvSource({
            "disable, DISABLE",
            "allow, ALLOW",
            "prefer, PREFER",
            "require, REQUIRE",
            "verify-ca, VERIFY_CA",
            "verify-full, VERIFY_FULL"
    })
    void buildConnectionConfigurationCarriesConfiguredSslMode(String sslMode, SSLMode expected) {
        assertEquals(expected, sslModeOf(connectionPool.buildConnectionConfiguration(propertiesWithSslMode(sslMode))));
    }

    @Test
    void buildConnectionConfigurationRejectsUnknownSslMode() {
        PostgresqlConnectionProperties properties = propertiesWithSslMode(UNKNOWN_SSL_MODE);

        assertThrows(IllegalArgumentException.class, () -> connectionPool.buildConnectionConfiguration(properties));
    }
}
