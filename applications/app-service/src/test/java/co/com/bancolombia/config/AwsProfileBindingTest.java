package co.com.bancolombia.config;

import co.com.bancolombia.r2dbc.config.PostgresqlConnectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads application.yaml + application-aws.yaml through Spring Boot's config data processing, with no
 * database and no AWS call. The Secrets Manager import stays in place but is turned off, and a fake
 * {@code db.*} property source stands in for the DB Secret.
 */
class AwsProfileBindingTest {

    private static final String AWS_PROFILE = "aws";
    private static final String DISABLE_SECRETS_MANAGER = "--spring.cloud.aws.secretsmanager.enabled=false";
    private static final String FAKE_SECRET_NAME = "--DB_SECRET_NAME=fake-db-secret";

    private static final String DB_HOST = "db.example.internal";
    private static final int DB_PORT = 5432;
    private static final String DB_NAME = "franchise_inventory";
    private static final String DB_USERNAME = "app_user";
    private static final String DB_PASSWORD = "fake-password";

    private static final Map<String, Object> FAKE_DB_SECRET = Map.of(
            "db.host", DB_HOST,
            "db.port", DB_PORT,
            "db.dbname", DB_NAME,
            "db.username", DB_USERNAME,
            "db.password", DB_PASSWORD);

    @Test
    void awsProfileMapsTheDbSecretToR2dbcProperties() {
        try (ConfigurableApplicationContext context = startAwsProfile()) {
            PostgresqlConnectionProperties properties = Binder.get(context.getEnvironment())
                    .bind("adapters.r2dbc", PostgresqlConnectionProperties.class)
                    .get();

            assertThat(properties).isEqualTo(new PostgresqlConnectionProperties(
                    DB_HOST, DB_PORT, DB_NAME, "public", DB_USERNAME, DB_PASSWORD, "require"));
        }
    }

    @Test
    void awsProfileFlywayUrlRequiresSsl() {
        try (ConfigurableApplicationContext context = startAwsProfile()) {
            ConfigurableEnvironment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.flyway.url"))
                    .isEqualTo("jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?sslmode=require");
            assertThat(environment.getProperty("spring.flyway.user")).isEqualTo(DB_USERNAME);
            assertThat(environment.getProperty("spring.flyway.password")).isEqualTo(DB_PASSWORD);
        }
    }

    @Test
    void awsProfileAllowsNoCorsOrigin() {
        try (ConfigurableApplicationContext context = startAwsProfile()) {
            assertThat(context.getBean(CorsOrigins.class).values()).isEmpty();
        }
    }

    private static ConfigurableApplicationContext startAwsProfile() {
        SpringApplication application = new SpringApplication(CorsOriginsConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(AWS_PROFILE);
        application.setDefaultProperties(FAKE_DB_SECRET);
        return application.run(DISABLE_SECRETS_MANAGER, FAKE_SECRET_NAME);
    }

    record CorsOrigins(List<String> values) {
    }

    /** Binds the origins the same way {@code CorsConfig} does. */
    @Configuration(proxyBeanMethods = false)
    static class CorsOriginsConfig {

        @Bean
        CorsOrigins corsOrigins(@Value("${cors.allowed-origins}") List<String> origins) {
            return new CorsOrigins(origins);
        }
    }
}
