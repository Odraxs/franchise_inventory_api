package co.com.bancolombia.config;

import co.com.bancolombia.api.config.ApiCredentialProperties;
import co.com.bancolombia.r2dbc.config.PostgresqlConnectionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loads application.yaml + application-aws.yaml through Spring Boot's config data processing, with no
 * database and no AWS call. Two setups:
 * <ul>
 *   <li>The Secrets Manager import stays in place but is turned off, and a fake {@code db.*} property
 *   source stands in for the DB Secret.</li>
 *   <li>The import runs for real against a fake {@link SecretsManagerClient} in the bootstrap registry, so
 *   the import string itself (both secrets, each with its prefix) is tested.</li>
 * </ul>
 */
class AwsProfileBindingTest {

    private static final String AWS_PROFILE = "aws";
    private static final String DISABLE_SECRETS_MANAGER = "--spring.cloud.aws.secretsmanager.enabled=false";
    private static final String FAKE_DB_SECRET_NAME = "fake-db-secret";
    private static final String FAKE_API_SECRET_NAME = "fake-api-secret";
    private static final String DB_SECRET_NAME_ARG = "--DB_SECRET_NAME=" + FAKE_DB_SECRET_NAME;
    private static final String API_SECRET_NAME_ARG = "--API_SECRET_NAME=" + FAKE_API_SECRET_NAME;

    private static final String DB_HOST = "db.example.internal";
    private static final int DB_PORT = 5432;
    private static final String DB_NAME = "franchise_inventory";
    private static final String DB_USERNAME = "app_user";
    private static final String DB_PASSWORD = "fake-password";

    private static final String API_USERNAME = "inventory-client";
    private static final String API_PASSWORD = "fake-api-password";

    private static final Map<String, Object> FAKE_DB_SECRET = Map.of(
            "db.host", DB_HOST,
            "db.port", DB_PORT,
            "db.dbname", DB_NAME,
            "db.username", DB_USERNAME,
            "db.password", DB_PASSWORD);

    /** Secret name to secret JSON, as Secrets Manager returns them. */
    private static final Map<String, String> FAKE_SECRETS = Map.of(
            FAKE_DB_SECRET_NAME, """
                    {"host": "%s", "port": %d, "dbname": "%s", "username": "%s", "password": "%s"}"""
                    .formatted(DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD),
            FAKE_API_SECRET_NAME, """
                    {"username": "%s", "password": "%s"}"""
                    .formatted(API_USERNAME, API_PASSWORD));

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

    @Test
    void awsProfileBindsTheApiCredentialFromTheApiSecret() {
        try (ConfigurableApplicationContext context = startAwsProfileWith(fakeSecretsManager())) {
            ApiCredentialProperties credential = Binder.get(context.getEnvironment())
                    .bind("security.client", ApiCredentialProperties.class)
                    .get();

            assertThat(credential.username()).isEqualTo(API_USERNAME);
            assertThat(credential.password()).isEqualTo(API_PASSWORD);
        }
    }

    @Test
    void awsProfileImportsTheDbSecretAndTheApiSecretInOneImport() {
        SecretsManagerClient secretsManager = fakeSecretsManager();

        try (ConfigurableApplicationContext context = startAwsProfileWith(secretsManager)) {
            PostgresqlConnectionProperties properties = Binder.get(context.getEnvironment())
                    .bind("adapters.r2dbc", PostgresqlConnectionProperties.class)
                    .get();

            assertThat(properties.host()).isEqualTo(DB_HOST);
            assertThat(properties.password()).isEqualTo(DB_PASSWORD);
        }

        ArgumentCaptor<GetSecretValueRequest> requests = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(secretsManager, times(2)).getSecretValue(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(GetSecretValueRequest::secretId)
                .containsExactlyInAnyOrder(FAKE_DB_SECRET_NAME, FAKE_API_SECRET_NAME);
    }

    private static ConfigurableApplicationContext startAwsProfile() {
        SpringApplication application = awsProfileApplication();
        application.setDefaultProperties(FAKE_DB_SECRET);
        return application.run(DISABLE_SECRETS_MANAGER, DB_SECRET_NAME_ARG, API_SECRET_NAME_ARG);
    }

    /** Spring Cloud AWS uses a SecretsManagerClient that is already in the bootstrap registry instead of building one. */
    private static ConfigurableApplicationContext startAwsProfileWith(SecretsManagerClient secretsManager) {
        SpringApplication application = awsProfileApplication();
        application.addBootstrapRegistryInitializer(registry ->
                registry.register(SecretsManagerClient.class, bootstrapContext -> secretsManager));
        return application.run(DB_SECRET_NAME_ARG, API_SECRET_NAME_ARG);
    }

    private static SpringApplication awsProfileApplication() {
        SpringApplication application = new SpringApplication(CorsOriginsConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(AWS_PROFILE);
        return application;
    }

    /** Answers GetSecretValue with the JSON of the requested fake secret. */
    private static SecretsManagerClient fakeSecretsManager() {
        SecretsManagerClient secretsManager = mock(SecretsManagerClient.class);
        when(secretsManager.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest request = invocation.getArgument(0);
            return GetSecretValueResponse.builder()
                    .name(request.secretId())
                    .secretString(FAKE_SECRETS.get(request.secretId()))
                    .build();
        });
        return secretsManager;
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
