package co.com.bancolombia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/** The API Credential of the single trusted Client. A blank or missing part stops the app at startup. */
@ConfigurationProperties(prefix = "security.client")
public record ApiCredentialProperties(String username, String password) {
    private static final String USERNAME_PROPERTY = "security.client.username";
    private static final String PASSWORD_PROPERTY = "security.client.password";
    private static final String BLANK_SUFFIX = " must be set and not blank";
    private static final String MASK = "******";

    public ApiCredentialProperties {
        Assert.hasText(username, USERNAME_PROPERTY + BLANK_SUFFIX);
        Assert.hasText(password, PASSWORD_PROPERTY + BLANK_SUFFIX);
    }

    /** Keeps the password out of logs and startup failure messages. */
    @Override
    public String toString() {
        return "ApiCredentialProperties[username=" + username + ", password=" + MASK + "]";
    }
}
