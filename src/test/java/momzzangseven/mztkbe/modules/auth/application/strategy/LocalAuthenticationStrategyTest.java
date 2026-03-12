package momzzangseven.mztkbe.modules.auth.application.strategy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalAuthenticationStrategy 단위 테스트")
class LocalAuthenticationStrategyTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private LocalAuthenticationStrategy strategy;

  private static final String EMAIL = "user@example.com";
  private static final String RAW_PASSWORD = "Password123!";
  // BCrypt 형식($2a$ + 56자)을 만족하는 테스트용 해시
  private static final String ENCODED_PASSWORD =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private User createActiveUser() {
    return User.builder()
        .id(1L)
        .email(EMAIL)
        .password(ENCODED_PASSWORD)
        .nickname("testuser")
        .authProvider(AuthProvider.LOCAL)
        .build();
  }

  private AuthenticationContext localContext() {
    return new AuthenticationContext(AuthProvider.LOCAL, EMAIL, RAW_PASSWORD, null, null);
  }

  // ============================================
  // supports()
  // ============================================

  @Test
  @DisplayName("supports()는 LOCAL을 반환")
  void supports_ReturnsLocal() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.LOCAL);
  }

  // ============================================
  // authenticate() - 성공 케이스
  // ============================================

  @Nested
  @DisplayName("authenticate() - 인증 성공")
  class AuthenticateSuccessTest {

    @Test
    @DisplayName("올바른 이메일/비밀번호로 인증 성공 시 기존 유저 반환")
    void authenticate_ValidCredentials_ReturnsExistingUser() {
      User user = createActiveUser();
      User savedUser = createActiveUser();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
      given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

      AuthenticatedUser result = strategy.authenticate(localContext());

      assertThat(result).isNotNull();
      assertThat(result.isNewUser()).isFalse();
      assertThat(result.user().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("인증 성공 시 마지막 로그인 시각 업데이트 후 저장")
    void authenticate_ValidCredentials_UpdatesLastLogin() {
      User user = createActiveUser();
      User savedUser = createActiveUser();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
      given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

      strategy.authenticate(localContext());

      verify(saveUserPort, times(1)).saveUser(any(User.class));
    }
  }

  // ============================================
  // authenticate() - 실패 케이스
  // ============================================

  @Nested
  @DisplayName("authenticate() - 인증 실패")
  class AuthenticateFailureTest {

    @Test
    @DisplayName("이메일이 없으면 InvalidCredentialsException 발생")
    void authenticate_MissingEmail_ThrowsException() {
      AuthenticationContext contextWithoutEmail =
          new AuthenticationContext(AuthProvider.LOCAL, null, RAW_PASSWORD, null, null);

      assertThatThrownBy(() -> strategy.authenticate(contextWithoutEmail))
          .isInstanceOf(InvalidCredentialsException.class);

      verifyNoInteractions(loadUserPort);
    }

    @Test
    @DisplayName("비밀번호가 없으면 InvalidCredentialsException 발생")
    void authenticate_MissingPassword_ThrowsException() {
      AuthenticationContext contextWithoutPassword =
          new AuthenticationContext(AuthProvider.LOCAL, EMAIL, null, null, null);

      assertThatThrownBy(() -> strategy.authenticate(contextWithoutPassword))
          .isInstanceOf(InvalidCredentialsException.class);

      verifyNoInteractions(loadUserPort);
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 UserNotFoundException 발생")
    void authenticate_UnknownEmail_ThrowsUserNotFoundException() {
      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.empty());
      given(loadUserPort.loadDeletedUserByEmail(EMAIL)).willReturn(Optional.empty());

      assertThatThrownBy(() -> strategy.authenticate(localContext()))
          .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 InvalidCredentialsException 발생")
    void authenticate_WrongPassword_ThrowsInvalidCredentialsException() {
      User user = createActiveUser();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

      assertThatThrownBy(() -> strategy.authenticate(localContext()))
          .isInstanceOf(InvalidCredentialsException.class);

      verifyNoInteractions(saveUserPort);
    }

    @Test
    @DisplayName("다른 provider의 이메일로 LOCAL 로그인 시도 시 InvalidCredentialsException 발생")
    void authenticate_WrongProvider_ThrowsInvalidCredentialsException() {
      User kakaoUser =
          User.builder()
              .id(2L)
              .email(EMAIL)
              .nickname("kakaouser")
              .authProvider(AuthProvider.KAKAO)
              .build();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.of(kakaoUser));

      assertThatThrownBy(() -> strategy.authenticate(localContext()))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }

  // ============================================
  // 탈퇴 유저 처리
  // ============================================

  @Nested
  @DisplayName("authenticate() - 탈퇴 유저 처리")
  class WithdrawnUserTest {

    @Test
    @DisplayName("탈퇴 유저 + 올바른 비밀번호 → UserWithdrawnException 발생 (계정 존재 노출 방지)")
    void authenticate_WithdrawnUser_CorrectPassword_ThrowsUserWithdrawnException() {
      User deletedUser =
          User.builder()
              .id(1L)
              .email(EMAIL)
              .password(ENCODED_PASSWORD)
              .nickname("testuser")
              .authProvider(AuthProvider.LOCAL)
              .build();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.empty());
      given(loadUserPort.loadDeletedUserByEmail(EMAIL)).willReturn(Optional.of(deletedUser));
      given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);

      assertThatThrownBy(() -> strategy.authenticate(localContext()))
          .isInstanceOf(UserWithdrawnException.class);
    }

    @Test
    @DisplayName("탈퇴 유저 + 잘못된 비밀번호 → InvalidCredentialsException 발생 (계정 열거 방지)")
    void authenticate_WithdrawnUser_WrongPassword_ThrowsInvalidCredentialsException() {
      User deletedUser =
          User.builder()
              .id(1L)
              .email(EMAIL)
              .password(ENCODED_PASSWORD)
              .nickname("testuser")
              .authProvider(AuthProvider.LOCAL)
              .build();

      given(loadUserPort.loadUserByEmail(EMAIL)).willReturn(Optional.empty());
      given(loadUserPort.loadDeletedUserByEmail(EMAIL)).willReturn(Optional.of(deletedUser));
      given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

      assertThatThrownBy(() -> strategy.authenticate(localContext()))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }
}
