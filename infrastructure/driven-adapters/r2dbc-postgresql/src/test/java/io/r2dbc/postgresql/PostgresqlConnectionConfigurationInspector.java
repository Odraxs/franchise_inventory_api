package io.r2dbc.postgresql;

import io.r2dbc.postgresql.client.SSLMode;

/**
 * Test-only bridge: {@link PostgresqlConnectionConfiguration} exposes its SSL settings only to its own
 * package, so this helper lives there to let tests read the SSL mode of a built configuration.
 */
public final class PostgresqlConnectionConfigurationInspector {

    private PostgresqlConnectionConfigurationInspector() {
    }

    public static SSLMode sslModeOf(PostgresqlConnectionConfiguration configuration) {
        return configuration.getSslConfig().getSslMode();
    }
}
