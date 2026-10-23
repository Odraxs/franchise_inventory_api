package co.com.bancolombia.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Every exchange needs the API Credential of the single trusted Client, sent with HTTP Basic,
 * except health (the ALB and ECS checks send no credential) and CORS preflight (browsers send none).
 * The chain is stateless: no session, security context store, saved request, cookie or CSRF token.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(ApiCredentialProperties.class)
public class SecurityConfig {
    private static final String HEALTH = "/actuator/health/**";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  ReactiveAuthenticationManager apiCredentialAuthenticationManager,
                                                  ServerCodecConfigurer codecs,
                                                  CorsConfigurationSource corsConfigurationSource) {
        ServerAuthenticationEntryPoint entryPoint = new ApiCredentialEntryPoint(codecs.getWriters());
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HEALTH).permitAll()
                        .anyExchange().authenticated())
                // Answers a preflight before authentication runs, with the CORS headers
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(basic -> basic
                        .authenticationManager(apiCredentialAuthenticationManager)
                        .authenticationEntryPoint(entryPoint)
                        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
                // SecurityHeadersConfig is the only source of security headers
                .headers(ServerHttpSecurity.HeaderSpec::disable)
                .build();
    }

    /** Declared as a bean, so Spring Boot backs off its default user with a generated password. */
    @Bean
    ReactiveAuthenticationManager apiCredentialAuthenticationManager(ApiCredentialProperties credential) {
        return new ApiCredentialAuthenticationManager(credential);
    }
}
