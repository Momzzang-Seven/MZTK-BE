package momzzangseven.mztkbe.global.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties loaded from application.yml.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens.
     * MUST be at least 256 bits (32 characters) for HS256.
     */
    private String secret;

    /**
     * Access token expiration time in milliseconds.
     * Default: 1 hour (3600000ms)
     */
    private Long accessTokenExpiration;

    /**
     * Refresh token expiration time in milliseconds.
     * Default: 7 days (604800000ms)
     */
    private Long refreshTokenExpiration;

    /**
     * Token issuer (e.g., "MZTK-BE")
     */
    private String issuer;

    /**
     * Token audience (e.g., "MZTK-Client")
     */
    private String audience;
}