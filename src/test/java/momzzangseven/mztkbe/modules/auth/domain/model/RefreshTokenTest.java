package momzzangseven.mztkbe.modules.auth.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.token.RefreshTokenInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshToken 도메인 모델 단위 테스트")
class RefreshTokenTest {

  private static final Long VALID_USER_ID = 1L;
  private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiJ9.validTokenValue12345678";

  private static RefreshToken createValidToken() {
    return RefreshToken.create(
        VALID_USER_ID, VALID_TOKEN, LocalDateTime.now().plusDays(1), LocalDateTime.now());
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
      LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
      LocalDateTime createdAt = LocalDateTime.now();

      RefreshToken token = RefreshToken.create(VALID_USER_ID, VALID_TOKEN, expiresAt, createdAt);

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

      assertThat(token.isValid()).isTrue();
      assertThat(token.isExpired()).isFalse();
      assertThat(token.isRevoked()).isFalse();
    }
  }

  @Nested
  @DisplayName("create() - 입력 검증 실패")
  class CreateValidationTest {

    @Test
    @DisplayName("userId가 null이면 예외 발생")
    void create_NullUserId_ThrowsException() {
      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      null, VALID_TOKEN, LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be a positive number");
    }

    @Test
    @DisplayName("userId가 0 이하이면 예외 발생")
    void create_NegativeUserId_ThrowsException() {
      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      -1L, VALID_TOKEN, LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tokenValue가 null이면 예외 발생")
    void create_NullTokenValue_ThrowsException() {
      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      VALID_USER_ID, null, LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value is required");
    }

    @Test
    @DisplayName("tokenValue가 빈 문자열이면 예외 발생")
    void create_BlankTokenValue_ThrowsException() {
      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      VALID_USER_ID, "   ", LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value is required");
    }

    @Test
    @DisplayName("tokenValue가 10자 미만이면 예외 발생")
    void create_TooShortTokenValue_ThrowsException() {
      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      VALID_USER_ID, "short", LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value too short");
    }

    @Test
    @DisplayName("tokenValue가 500자 초과이면 예외 발생")
    void create_TooLongTokenValue_ThrowsException() {
      String tooLong = "a".repeat(501);

      assertThatThrownBy(
              () ->
                  RefreshToken.create(
                      VALID_USER_ID, tooLong, LocalDateTime.now().plusDays(1), LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Token value too long");
    }

    @Test
    @DisplayName("expiresAt이 null이면 예외 발생")
    void create_NullExpiresAt_ThrowsException() {
      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, VALID_TOKEN, null, LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expiration time is required");
    }

    @Test
    @DisplayName("expiresAt이 과거이면 예외 발생")
    void create_PastExpiresAt_ThrowsException() {
      LocalDateTime past = LocalDateTime.now().minusSeconds(1);

      assertThatThrownBy(
              () -> RefreshToken.create(VALID_USER_ID, VALID_TOKEN, past, LocalDateTime.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expiration time must be in the future");
    }

    @Test
    @DisplayName("expiresAt이 7일 초과이면 예외 발생")
    void create_ExpiresAtBeyond7Days_ThrowsException() {
      LocalDateTime beyond7Days = LocalDateTime.now().plusDays(8);

      assertThatThrownBy(
              () ->
                  RefreshToken.create(VALID_USER_ID, VALID_TOKEN, beyond7Days, LocalDateTime.now()))
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

      assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료 시각이 과거이면 만료됨")
    void isExpired_PastExpiry_ReturnsTrue() {
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().minusSeconds(1))
              .createdAt(LocalDateTime.now().minusDays(1))
              .build();

      assertThat(token.isExpired()).isTrue();
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
      RefreshToken token = createValidToken();

      token.revoke();

      assertThat(token.isRevoked()).isTrue();
      assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 revoke된 토큰에 revoke 재호출해도 예외 미발생 (멱등성)")
    void revoke_AlreadyRevoked_NoException() {
      RefreshToken token = createValidToken();
      token.revoke();

      assertThatCode(token::revoke).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("revoke 후 isValid=false")
    void revoke_TokenBecomesInvalid() {
      RefreshToken token = createValidToken();
      token.revoke();

      assertThat(token.isValid()).isFalse();
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
      RefreshToken token = createValidToken();

      token.markAsUsed();

      assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("revoke된 토큰에 markAsUsed 호출 시 예외 발생")
    void markAsUsed_RevokedToken_ThrowsException() {
      RefreshToken token = createValidToken();
      token.revoke();

      assertThatThrownBy(token::markAsUsed).isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    @DisplayName("만료된 토큰에 markAsUsed 호출 시 예외 발생")
    void markAsUsed_ExpiredToken_ThrowsException() {
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().minusSeconds(1))
              .createdAt(LocalDateTime.now().minusDays(1))
              .build();

      assertThatThrownBy(expiredToken::markAsUsed).isInstanceOf(RefreshTokenInvalidException.class);
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

      assertThat(token.wasRecentlyUsed(5)).isFalse();
    }

    @Test
    @DisplayName("방금 사용된 토큰은 wasRecentlyUsed=true")
    void wasRecentlyUsed_JustUsed_ReturnsTrue() {
      RefreshToken token = createValidToken();
      token.markAsUsed();

      assertThat(token.wasRecentlyUsed(5)).isTrue();
    }

    @Test
    @DisplayName("오래 전 사용된 토큰은 wasRecentlyUsed=false")
    void wasRecentlyUsed_UsedLongAgo_ReturnsFalse() {
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().plusDays(1))
              .createdAt(LocalDateTime.now().minusDays(1))
              .usedAt(LocalDateTime.now().minusHours(2))
              .build();

      assertThat(token.wasRecentlyUsed(5)).isFalse();
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

      assertThat(token.getRemainingSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("만료된 토큰의 remainingSeconds는 0")
    void getRemainingSeconds_ExpiredToken_ReturnsZero() {
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().minusSeconds(1))
              .createdAt(LocalDateTime.now().minusDays(1))
              .build();

      assertThat(expiredToken.getRemainingSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("revoke된 토큰의 remainingSeconds는 0")
    void getRemainingSeconds_RevokedToken_ReturnsZero() {
      RefreshToken token = createValidToken();
      token.revoke();

      assertThat(token.getRemainingSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("만료 임박한 토큰은 isExpiringSoon=true")
    void isExpiringSoon_NearExpiry_ReturnsTrue() {
      RefreshToken token =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().plusMinutes(2))
              .createdAt(LocalDateTime.now())
              .build();

      assertThat(token.isExpiringSoon(5)).isTrue();
    }

    @Test
    @DisplayName("만료까지 충분히 남은 토큰은 isExpiringSoon=false")
    void isExpiringSoon_FarExpiry_ReturnsFalse() {
      RefreshToken token = createValidToken(); // +1일

      assertThat(token.isExpiringSoon(5)).isFalse();
    }

    @Test
    @DisplayName("이미 만료된 토큰은 isExpiringSoon=true")
    void isExpiringSoon_AlreadyExpired_ReturnsTrue() {
      RefreshToken expiredToken =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().minusSeconds(1))
              .createdAt(LocalDateTime.now().minusDays(1))
              .build();

      assertThat(expiredToken.isExpiringSoon(5)).isTrue();
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
      RefreshToken token1 = createValidToken();
      RefreshToken token2 =
          RefreshToken.builder()
              .userId(99L)
              .tokenValue(VALID_TOKEN)
              .expiresAt(LocalDateTime.now().plusHours(1))
              .createdAt(LocalDateTime.now())
              .build();

      assertThat(token1).isEqualTo(token2);
      assertThat(token1.hashCode()).isEqualTo(token2.hashCode());
    }

    @Test
    @DisplayName("다른 tokenValue를 가진 두 토큰은 동등하지 않음")
    void equals_DifferentTokenValue_AreNotEqual() {
      RefreshToken token1 = createValidToken();
      RefreshToken token2 =
          RefreshToken.create(
              VALID_USER_ID,
              "anotherValidTokenValue1234",
              LocalDateTime.now().plusDays(1),
              LocalDateTime.now());

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
      RefreshToken tokenWithNullValue =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .expiresAt(LocalDateTime.now().plusDays(1))
              .createdAt(LocalDateTime.now())
              .build();

      assertThat(tokenWithNullValue.hashCode()).isZero();
    }

    @Test
    @DisplayName("tokenValue가 null이면 equals는 false 반환")
    void equals_NullTokenValue_ReturnsFalse() {
      RefreshToken tokenA =
          RefreshToken.builder()
              .userId(VALID_USER_ID)
              .expiresAt(LocalDateTime.now().plusDays(1))
              .createdAt(LocalDateTime.now())
              .build();
      RefreshToken tokenB = createValidToken();

      assertThat(tokenA.equals(tokenB)).isFalse();
    }
  }
}
