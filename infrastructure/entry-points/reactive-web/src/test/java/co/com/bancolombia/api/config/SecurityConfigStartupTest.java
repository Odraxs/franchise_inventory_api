package co.com.bancolombia.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The app refuses to start without a complete API Credential. */
class SecurityConfigStartupTest {
    private static final String PASSWORD_KEY = "security.client.password";
    private static final String USERNAME = "security.client.username=test-client";
    private static final String PASSWORD = "security.client.password=test-only-api-credential-password-0123456789";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withBean(ServerCodecConfigurer.class, ServerCodecConfigurer::create);

    @Test
    void startsWithACompleteApiCredential() {
        contextRunner.withPropertyValues(USERNAME, PASSWORD)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SecurityWebFilterChain.class));
    }

    @Test
    void failsWithoutPassword() {
        contextRunner.withPropertyValues(USERNAME)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining(PASSWORD_KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void failsWithBlankPassword(String password) {
        contextRunner.withPropertyValues(USERNAME)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("blankPassword", Map.of(PASSWORD_KEY, password))))
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining(PASSWORD_KEY));
    }

    @Test
    void failsWithoutUsername() {
        contextRunner.withPropertyValues(PASSWORD)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("security.client.username"));
    }
}
