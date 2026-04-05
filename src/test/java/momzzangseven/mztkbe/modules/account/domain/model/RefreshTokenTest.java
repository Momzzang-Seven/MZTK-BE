package momzzangseven.mztkbe.modules.account.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import momzzangseven.mztkbe.global.error.token.RefreshTokenInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshToken 도메인 모델 단위 테스트")
class RefreshTokenTest {

  private static final Long VALID_USER_ID = 1L;
  private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiJ9.validTokenValue12345678";

  private static RefreshToken createValidToken() {
    Instant now = Instant.now();
    return RefreshToken.create(VALID_USER_ID, VALID_TOKEN, now.plus(Duration.ofDays(1)), now);
  }

  // ============================================
  // create() - 팩토리 메서드 검증
  // ============================================

  @Nested
  @DisplayName("create() - 유효한 입력으로 생성")
  class CreateTest {

    @Test
    @DisplayName("정상 입력으로 RefreshToken 생성 성공")
    void create_ValidInput_Success() {
      Instant now = Instant.now();
      Instant expiresAt = now.plus(Duration.ofDays(1));

      RefreshToken token = RefreshToken.create(VALID_USER_ID, VALID_TOKEN, expiresAt, now);

      assertThat(token).isNotNull();
      assertThat(token.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(token.getTokenValue()).isEqualTo(VALID_TOKEN);
      assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
      assertThat(token.getRevokedAt()).isNull();
      assertThat(token.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("생성 직후 유효 상태(isValid=true)")
    void create_NewToken_IsValid() {
      RefreshToken token = createValidToken();

      assertThat(token.isValid(Instant.now())).isTrue();
      assertThat(token.isExpired(Instant.now())).isFalse();
      assertThat(token.isRevoked()).isFalse();
    }
  }

  @Nested
  @DisplayName("create() - 입력 검증 실패")
  class CreateValidationTest {

    @Test
    @DisplayName("userId가 null이면 예외 발생")
    void create_NullUserId_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(
              () -> RefreshToken.create(null, VALID_TOKEN, now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be a positive number");
    }

    @Test
    @DisplayName("userId가 0 이하이면 예외 발생")
    void create_NegativeUserId_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(
              () -> RefreshToken.create(-1L, VALID_TOKEN, now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tokenValue가 null이면 예외 발생")
    void create_NullTokenValue_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, null, now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value is required");
    }

    @Test
    @DisplayName("tokenValue가 빈 문자열이면 예외 발생")
    void create_BlankTokenValue_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, "   ", now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value is required");
    }

    @Test
    @DisplayName("tokenValue가 10자 미만이면 예외 발생")
    void create_TooShortTokenValue_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, "short", now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value too short");
    }

    @Test
    @DisplayName("tokenValue가 500자 초과이면 예외 발생")
    void create_TooLongTokenValue_ThrowsException() {
      Instant now = Instant.now();
      String tooLong = "a".repeat(501);

      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, tooLong, now.plus(Duration.ofDays(1)), now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value too long");
    }

    @Test
    @DisplayName("expiresAt이 null이면 예외 발생")
    void create_NullExpiresAt_ThrowsException() {
      Instant now = Instant.now();
      assertThatThrownBy(() -> RefreshToken.create(VALID_USER_ID, VALID_TOKEN, null, now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expiration time is required");
    }

    @Test
    @DisplayName("expiresAt이 과거이면 예외 발생")
    void create_PastExpiresAt_ThrowsException() {
      Instant now = Instant.now();
      Instant past = now.minusSeconds(1);

      assertThatThrownBy(() -> RefreshToken.create(VALID_USER_ID, VALID_TOKEN, past, now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expiration time must be in the future");
    }

    @Test
    @DisplayName("expiresAt이 7일 초과이면 예외 발생")
    void create_ExpiresAtBeyond7Days_ThrowsException() {
      Instant now = Instant.now();
      Instant beyond7Days = now.plus(Duration.ofDays(8));

      assertThatThrownBy(() -> RefreshToken.create(VALID_USER_ID, VALID_TOKEN, beyond7Days, now))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("7 days");
    }
  }

  // ============================================
  // isExpired() - 만료 검증
  // ============================================

  @Nested
  @DisplayName("isExpired()")
  class IsExpiredTest {

    @Test
    @DisplayName("만료 시각이 미래이면 만료되지 않음")
    void isExpired_FutureExpiry_ReturnsFalse() {
      RefreshToken token = createValidToken();

      assertThat(token.isExpired(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("만료 시각이 과거이면 만료됨")
    void isExpired_PastExpiry_ReturnsTrue() {
      Instant now = Instant.now();
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.minusSeconds(1))
              .createdAt(now.minus(Duration.ofDays(1)))
              .build();

      assertThat(token.isExpired(now)).isTrue();
    }
  }

  // ============================================
  // isRevoked() / revoke()
  // ============================================

  @Nested
  @DisplayName("revoke() / isRevoked()")
  class RevokeTest {

    @Test
    @DisplayName("revoke 호출 전에는 isRevoked=false")
    void isRevoked_BeforeRevoke_ReturnsFalse() {
      RefreshToken token = createValidToken();

      assertThat(token.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("revoke 호출 후 isRevoked=true")
    void revoke_CallsRevoke_TokenBecomeRevoked() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();

      RefreshToken revokedToken = token.revoke(now);

      assertThat(revokedToken.isRevoked()).isTrue();
      assertThat(revokedToken.getRevokedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("이미 revoke된 토큰에 revoke 재호출해도 예외 미발생 (멱등성)")
    void revoke_AlreadyRevoked_NoException() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();
      RefreshToken revokedToken = token.revoke(now);

      assertThatCode(() -> revokedToken.revoke(now)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("revoke 후 isValid=false")
    void revoke_TokenBecomesInvalid() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();
      RefreshToken revokedToken = token.revoke(now);

      assertThat(revokedToken.isValid(now)).isFalse();
    }
  }

  // ============================================
  // markAsUsed()
  // ============================================

  @Nested
  @DisplayName("markAsUsed()")
  class MarkAsUsedTest {

    @Test
    @DisplayName("유효한 토큰에 markAsUsed 호출 시 usedAt 기록")
    void markAsUsed_ValidToken_SetsUsedAt() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();

      RefreshToken usedToken = token.markAsUsed(now);

      assertThat(usedToken.getUsedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("revoke된 토큰에 markAsUsed 호출 시 예외 발생")
    void markAsUsed_RevokedToken_ThrowsException() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();
      RefreshToken revokedToken = token.revoke(now);

      assertThatThrownBy(() -> revokedToken.markAsUsed(now))
          .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    @DisplayName("만료된 토큰에 markAsUsed 호출 시 예외 발생")
    void markAsUsed_ExpiredToken_ThrowsException() {
      Instant now = Instant.now();
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.minusSeconds(1))
              .createdAt(now.minus(Duration.ofDays(1)))
              .build();

      assertThatThrownBy(() -> expiredToken.markAsUsed(now))
          .isInstanceOf(RefreshTokenInvalidException.class);
    }
  }

  // ============================================
  // wasRecentlyUsed()
  // ============================================

  @Nested
  @DisplayName("wasRecentlyUsed()")
  class WasRecentlyUsedTest {

    @Test
    @DisplayName("usedAt이 null이면 false 반환")
    void wasRecentlyUsed_NotUsed_ReturnsFalse() {
      RefreshToken token = createValidToken();

      assertThat(token.wasRecentlyUsed(5, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("방금 사용된 토큰은 wasRecentlyUsed=true")
    void wasRecentlyUsed_JustUsed_ReturnsTrue() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();
      RefreshToken usedToken = token.markAsUsed(now);

      assertThat(usedToken.wasRecentlyUsed(5, now)).isTrue();
    }

    @Test
    @DisplayName("오래 전 사용된 토큰은 wasRecentlyUsed=false")
    void wasRecentlyUsed_UsedLongAgo_ReturnsFalse() {
      Instant now = Instant.now();
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.plus(Duration.ofDays(1)))
              .createdAt(now.minus(Duration.ofDays(1)))
              .usedAt(now.minus(Duration.ofHours(2)))
              .build();

      assertThat(token.wasRecentlyUsed(5, now)).isFalse();
    }
  }

  // ============================================
  // getRemainingSeconds() / isExpiringSoon()
  // ============================================

  @Nested
  @DisplayName("getRemainingSeconds() / isExpiringSoon()")
  class ExpiryCheckTest {

    @Test
    @DisplayName("유효한 토큰의 remainingSeconds는 양수")
    void getRemainingSeconds_ValidToken_ReturnsPositive() {
      RefreshToken token = createValidToken();

      assertThat(token.getRemainingSeconds(Instant.now())).isGreaterThan(0);
    }

    @Test
    @DisplayName("만료된 토큰의 remainingSeconds는 0")
    void getRemainingSeconds_ExpiredToken_ReturnsZero() {
      Instant now = Instant.now();
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.minusSeconds(1))
              .createdAt(now.minus(Duration.ofDays(1)))
              .build();

      assertThat(expiredToken.getRemainingSeconds(now)).isEqualTo(0);
    }

    @Test
    @DisplayName("revoke된 토큰의 remainingSeconds는 0")
    void getRemainingSeconds_RevokedToken_ReturnsZero() {
      Instant now = Instant.now();
      RefreshToken token = createValidToken();
      RefreshToken revokedToken = token.revoke(now);

      assertThat(revokedToken.getRemainingSeconds(now)).isEqualTo(0);
    }

    @Test
    @DisplayName("만료 임박한 토큰은 isExpiringSoon=true")
    void isExpiringSoon_NearExpiry_ReturnsTrue() {
      Instant now = Instant.now();
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.plus(Duration.ofMinutes(2)))
              .createdAt(now)
              .build();

      assertThat(token.isExpiringSoon(5, now)).isTrue();
    }

    @Test
    @DisplayName("만료까지 충분히 남은 토큰은 isExpiringSoon=false")
    void isExpiringSoon_FarExpiry_ReturnsFalse() {
      RefreshToken token = createValidToken(); // +1일

      assertThat(token.isExpiringSoon(5, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("이미 만료된 토큰은 isExpiringSoon=true")
    void isExpiringSoon_AlreadyExpired_ReturnsTrue() {
      Instant now = Instant.now();
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.minusSeconds(1))
              .createdAt(now.minus(Duration.ofDays(1)))
              .build();

      assertThat(expiredToken.isExpiringSoon(5, now)).isTrue();
    }
  }

  // ============================================
  // equals / hashCode
  // ============================================

  @Nested
  @DisplayName("equals() / hashCode() - tokenValue 기반 동등성")
  class EqualityTest {

    @Test
    @DisplayName("동일 tokenValue를 가진 두 토큰은 동등")
    void equals_SameTokenValue_AreEqual() {
      Instant now = Instant.now();
      RefreshToken token1 = createValidToken();
      RefreshToken token2 =
          RefreshToken.builder()
              .userId(99L)
              .tokenValue(VALID_TOKEN)
              .expiresAt(now.plus(Duration.ofHours(1)))
              .createdAt(now)
              .build();

      assertThat(token1).isEqualTo(token2);
      assertThat(token1.hashCode()).isEqualTo(token2.hashCode());
    }

    @Test
    @DisplayName("다른 tokenValue를 가진 두 토큰은 동등하지 않음")
    void equals_DifferentTokenValue_AreNotEqual() {
      Instant now = Instant.now();
      RefreshToken token1 = createValidToken();
      RefreshToken token2 =
          RefreshToken.create(
              VALID_USER_ID, "anotherValidTokenValue1234", now.plus(Duration.ofDays(1)), now);

      assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("같은 참조(this == o)이면 true 반환")
    void equals_SameReference_ReturnsTrue() {
      RefreshToken token = createValidToken();
      assertThat(token.equals(token)).isTrue();
    }

    @Test
    @DisplayName("null과 비교하면 false 반환")
    void equals_Null_ReturnsFalse() {
      RefreshToken token = createValidToken();
      assertThat(token.equals(null)).isFalse();
    }

    @Test
    @DisplayName("다른 클래스 객체와 비교하면 false 반환")
    void equals_DifferentClass_ReturnsFalse() {
      RefreshToken token = createValidToken();
      assertThat(token.equals("not a token")).isFalse();
    }

    @Test
    @DisplayName("tokenValue가 null인 토큰의 hashCode는 0")
    void hashCode_NullTokenValue_ReturnsZero() {
      Instant now = Instant.now();
      RefreshToken tokenWithNullValue =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .expiresAt(now.plus(Duration.ofDays(1)))
              .createdAt(now)
              .build();

      assertThat(tokenWithNullValue.hashCode()).isZero();
    }

    @Test
    @DisplayName("tokenValue가 null이면 equals는 false 반환")
    void equals_NullTokenValue_ReturnsFalse() {
      Instant now = Instant.now();
      RefreshToken tokenA =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .expiresAt(now.plus(Duration.ofDays(1)))
              .createdAt(now)
              .build();
      RefreshToken tokenB = createValidToken();

      assertThat(tokenA.equals(tokenB)).isFalse();
    }
  }
}
