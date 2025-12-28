package momzzangseven.mztkbe.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 *
 * <p>Responsibilities: - Generate access tokens and refresh tokens - Validate token signatures and
 * expiration - Extract user information from tokens
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;

  // ========== Token Generation ==========

  /**
   * Generate an access token for authenticated user.
   *
   * @param userId User's unique identifier
   * @param email User's email
   * @param role User's role
   * @return JWT access token
   */
  public String generateAccessToken(Long userId, String email, UserRole role) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

    return Jwts.builder()
        .header()
        .type("JWT")
        .and()
        .subject(userId.toString()) // User PK
        .claim("email", email)
        .claim("role", role.name())
        .claim("type", "access")
        .issuer(jwtProperties.getIssuer())
        .audience()
        .add(jwtProperties.getAudience())
        .and()
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(getSigningKey())
        .compact();
  }

  /**
   * Generate a refresh token for authenticated user.
   *
   * @param userId User's unique identifier
   * @return JWT refresh token
   */
  public String generateRefreshToken(Long userId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

    return Jwts.builder()
        .header()
        .type("JWT")
        .and()
        .subject(userId.toString())
        .id(UUID.randomUUID().toString())
        .claim("type", "refresh")
        .issuer(jwtProperties.getIssuer())
        .audience()
        .add(jwtProperties.getAudience())
        .and()
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(getSigningKey())
        .compact();
  }

  // ========== Token Validation ==========

  /**
   * Validate token signature and expiration.
   *
   * @param token JWT token to validate
   * @return true if valid, false otherwise
   */
  public boolean validateToken(String token) {
    try {
      Jwts.parser()
          .verifyWith(getSigningKey())
          .requireIssuer(jwtProperties.getIssuer())
          .build()
          .parseSignedClaims(token);

      log.debug("Token validation successful");
      return true;

    } catch (SignatureException e) {
      log.error("Invalid JWT signature: {}", e.getMessage());
    } catch (MalformedJwtException e) {
      log.error("Invalid JWT token: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      log.error("Expired JWT token: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.error("Unsupported JWT token: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.error("JWT claims string is empty: {}", e.getMessage());
    }

    return false;
  }

  /**
   * Validate that the token is an access token.
   *
   * @param token JWT token
   * @return true if access token, false otherwise
   */
  public boolean isAccessToken(String token) {
    try {
      Claims claims = getClaims(token);
      String type = claims.get("type", String.class);
      return "access".equals(type);
    } catch (Exception e) {
      log.error("Error checking token type: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Validate that the token is a refresh token.
   *
   * @param token JWT token
   * @return true if refresh token, false otherwise
   */
  public boolean isRefreshToken(String token) {
    try {
      Claims claims = getClaims(token);
      String type = claims.get("type", String.class);
      return "refresh".equals(type);
    } catch (Exception e) {
      log.error("Error checking token type: {}", e.getMessage());
      return false;
    }
  }

  // ========== Token Information Extraction ==========

  /*
   * return access token expiration in application.yml (in milliseconds)
   */
  public long getAccessTokenExpiresIn() {
    return jwtProperties.getAccessTokenExpiration();
  }

  /*
   * return refresh token expiration in application.yml (in milliseconds)
   */
  public long getRefreshTokenExpiresIn() {
    return jwtProperties.getRefreshTokenExpiration();
  }

  /**
   * Extract user ID from token.
   *
   * @param token JWT token
   * @return User ID
   */
  public Long getUserIdFromToken(String token) {
    Claims claims = getClaims(token);
    return Long.parseLong(claims.getSubject());
  }

  /**
   * Extract email from token.
   *
   * @param token JWT token
   * @return User's email
   */
  public String getEmailFromToken(String token) {
    Claims claims = getClaims(token);
    return claims.get("email", String.class);
  }

  /**
   * Extract role from token.
   *
   * @param token JWT token
   * @return User's role
   */
  public UserRole getRoleFromToken(String token) {
    Claims claims = getClaims(token);
    String roleName = claims.get("role", String.class);
    return UserRole.valueOf(roleName);
  }

  /**
   * Get token expiration date.
   *
   * @param token JWT token
   * @return Expiration date
   */
  public Date getExpirationFromToken(String token) {
    Claims claims = getClaims(token);
    return claims.getExpiration();
  }

  /**
   * Get remaining time until token expires (in milliseconds).
   *
   * @param token JWT token
   * @return Remaining time in milliseconds
   */
  public Long getRemainingTime(String token) {
    Date expiration = getExpirationFromToken(token);
    Date now = new Date();
    return expiration.getTime() - now.getTime();
  }

  /**
   * Check if token is expired.
   *
   * @param token JWT token
   * @return true if expired, false otherwise
   */
  public boolean isTokenExpired(String token) {
    try {
      Date expiration = getExpirationFromToken(token);
      return expiration.before(new Date());
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  // ========== Private Helper Methods ==========

  /**
   * Parse and extract claims from token.
   *
   * @param token JWT token
   * @return Claims object
   */
  private Claims getClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }

  /**
   * Get signing key from secret. Uses HMAC-SHA256 algorithm.
   *
   * @return SecretKey for signing
   */
  private SecretKey getSigningKey() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
