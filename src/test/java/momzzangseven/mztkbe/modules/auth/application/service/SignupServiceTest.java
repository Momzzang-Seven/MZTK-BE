package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

import momzzangseven.mztkbe.global.error.DuplicateEmailException;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;
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
@DisplayName("SignupService 단위 테스트")
class SignupServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private SignupService signupService;

  private static final String VALID_EMAIL = "newuser@example.com";
  private static final String VALID_PASSWORD = "Password123!";
  private static final String VALID_NICKNAME = "newuser";
  // BCrypt 형식($2a$ + 56자)을 만족하는 테스트용 해시
  private static final String ENCODED_PASSWORD =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private User createSavedUser() {
    return User.builder()
        .id(42L)
        .email(VALID_EMAIL)
        .password(ENCODED_PASSWORD)
        .nickname(VALID_NICKNAME)
        .authProvider(AuthProvider.LOCAL)
        .build();
  }

  // ============================================
  // 성공 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 회원가입 성공")
  class SignupSuccessTest {

    @Test
    @DisplayName("정상 입력으로 회원가입 시 userId 반환")
    void execute_ValidCommand_ReturnsSignupResult() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);
      User savedUser = createSavedUser();

      given(loadUserPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

      SignupResult result = signupService.execute(command);

      assertThat(result).isNotNull();
      assertThat(result.userId()).isEqualTo(42L);
      assertThat(result.email()).isEqualTo(VALID_EMAIL);
      assertThat(result.nickname()).isEqualTo(VALID_NICKNAME);
    }

    @Test
    @DisplayName("회원가입 시 비밀번호가 BCrypt로 인코딩되어 저장")
    void execute_ValidCommand_EncodesPassword() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);
      User savedUser = createSavedUser();

      given(loadUserPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

      signupService.execute(command);

      verify(passwordEncoder, times(1)).encode(VALID_PASSWORD);
      verify(saveUserPort, times(1)).saveUser(any(User.class));
    }

    @Test
    @DisplayName("저장된 유저에 이메일 중복 체크 수행 후 저장")
    void execute_ChecksEmailDuplication_BeforeSaving() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);
      User savedUser = createSavedUser();

      given(loadUserPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(anyString())).willReturn(ENCODED_PASSWORD);
      given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

      signupService.execute(command);

      // 중복 체크 먼저, 그 다음 저장
      var inOrder = inOrder(loadUserPort, saveUserPort);
      inOrder.verify(loadUserPort).existsByEmail(VALID_EMAIL);
      inOrder.verify(saveUserPort).saveUser(any(User.class));
    }
  }

  // ============================================
  // 실패 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 실패 케이스")
  class SignupFailureTest {

    @Test
    @DisplayName("이메일 중복 시 DuplicateEmailException 발생")
    void execute_DuplicateEmail_ThrowsDuplicateEmailException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);

      given(loadUserPort.existsByEmail(VALID_EMAIL)).willReturn(true);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(DuplicateEmailException.class);

      verifyNoInteractions(passwordEncoder, saveUserPort);
    }

    @Test
    @DisplayName("이메일이 null이면 validate()에서 예외 발생")
    void execute_NullEmail_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(null, VALID_PASSWORD, VALID_NICKNAME);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email is required");

      verifyNoInteractions(loadUserPort, passwordEncoder, saveUserPort);
    }

    @Test
    @DisplayName("비밀번호가 null이면 validate()에서 예외 발생")
    void execute_NullPassword_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, null, VALID_NICKNAME);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Password is required");

      verifyNoInteractions(loadUserPort, passwordEncoder, saveUserPort);
    }

    @Test
    @DisplayName("닉네임이 null이면 validate()에서 예외 발생")
    void execute_NullNickname_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, null);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname is required");

      verifyNoInteractions(loadUserPort, passwordEncoder, saveUserPort);
    }
  }
}
