package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.DuplicateEmailException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.account.application.dto.SignupResult;
import momzzangseven.mztkbe.modules.account.application.port.out.CreateAccountUserPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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

  @Mock private LoadAccountUserInfoPort loadAccountUserInfoPort;
  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private CreateAccountUserPort createAccountUserPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private SignupService signupService;

  private static final String VALID_EMAIL = "newuser@example.com";
  private static final String VALID_PASSWORD = "Password123!";
  private static final String VALID_NICKNAME = "newuser";
  // BCrypt 형식($2a$ + 56자)을 만족하는 테스트용 해시
  private static final String ENCODED_PASSWORD =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private AccountUserSnapshot createSavedSnapshot() {
    return new AccountUserSnapshot(42L, VALID_EMAIL, VALID_NICKNAME, null, "USER");
  }

  private AccountUserSnapshot createSavedSnapshot(String role) {
    return new AccountUserSnapshot(42L, VALID_EMAIL, VALID_NICKNAME, null, role);
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
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);
      AccountUserSnapshot snapshot = createSavedSnapshot();

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      SignupResult result = signupService.execute(command);

      assertThat(result).isNotNull();
      assertThat(result.userId()).isEqualTo(42L);
      assertThat(result.email()).isEqualTo(VALID_EMAIL);
      assertThat(result.nickname()).isEqualTo(VALID_NICKNAME);
    }

    @Test
    @DisplayName("회원가입 시 비밀번호가 BCrypt로 인코딩되어 저장")
    void execute_ValidCommand_EncodesPassword() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);
      AccountUserSnapshot snapshot = createSavedSnapshot();

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      signupService.execute(command);

      verify(passwordEncoder, times(1)).encode(VALID_PASSWORD);
      verify(saveUserAccountPort, times(1)).save(any());
    }

    @Test
    @DisplayName("[M-1] USER role 명시 시 USER role로 생성")
    void execute_WithUserRole_ReturnsSignupResultWithUserRole() {
      SignupCommand command =
          new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, "USER");
      AccountUserSnapshot snapshot = createSavedSnapshot("USER");

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      SignupResult result = signupService.execute(command);

      assertThat(result.role()).isEqualTo("USER");
      verify(createAccountUserPort)
          .createUser(eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER"));
    }

    @Test
    @DisplayName("[M-2] TRAINER role 명시 시 TRAINER role로 생성")
    void execute_WithTrainerRole_ReturnsSignupResultWithTrainerRole() {
      SignupCommand command =
          new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, "TRAINER");
      AccountUserSnapshot snapshot = createSavedSnapshot("TRAINER");

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("TRAINER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      SignupResult result = signupService.execute(command);

      assertThat(result.role()).isEqualTo("TRAINER");
      verify(createAccountUserPort)
          .createUser(eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("TRAINER"));
    }

    @Test
    @DisplayName("[M-3] role이 null이면 기본값 USER로 생성")
    void execute_WithNullRole_DefaultsToUser() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);
      AccountUserSnapshot snapshot = createSavedSnapshot("USER");

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      SignupResult result = signupService.execute(command);

      assertThat(result.role()).isEqualTo("USER");
      verify(createAccountUserPort)
          .createUser(eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER"));
    }

    @Test
    @DisplayName("[M-4] SignupResult에 role 필드 포함 확인")
    void execute_ReturnsSignupResultWithRole() {
      SignupCommand command =
          new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, "TRAINER");
      AccountUserSnapshot snapshot = createSavedSnapshot("TRAINER");

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("TRAINER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      SignupResult result = signupService.execute(command);

      assertThat(result).isNotNull();
      assertThat(result.role()).isEqualTo("TRAINER");
    }

    @Test
    @DisplayName("저장된 유저에 이메일 중복 체크 수행 후 저장")
    void execute_ChecksEmailDuplication_BeforeSaving() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);
      AccountUserSnapshot snapshot = createSavedSnapshot();

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(false);
      given(passwordEncoder.encode(anyString())).willReturn(ENCODED_PASSWORD);
      given(
              createAccountUserPort.createUser(
                  eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER")))
          .willReturn(snapshot);
      given(saveUserAccountPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      signupService.execute(command);

      // 중복 체크 먼저, 그 다음 저장
      var inOrder = inOrder(loadAccountUserInfoPort, createAccountUserPort);
      inOrder.verify(loadAccountUserInfoPort).existsByEmail(VALID_EMAIL);
      inOrder
          .verify(createAccountUserPort)
          .createUser(eq(VALID_EMAIL), eq(VALID_NICKNAME), any(), eq("USER"));
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
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL)).willReturn(Optional.empty());
      given(loadAccountUserInfoPort.existsByEmail(VALID_EMAIL)).willReturn(true);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(DuplicateEmailException.class);

      verifyNoInteractions(passwordEncoder, createAccountUserPort, saveUserAccountPort);
    }

    @Test
    @DisplayName("탈퇴 계정 이메일로 회원가입 시 UserWithdrawnException 발생")
    void execute_WithdrawnEmail_ThrowsUserWithdrawnException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME, null);
      UserAccount deletedAccount =
          UserAccount.builder()
              .id(1L)
              .userId(1L)
              .provider(AuthProvider.LOCAL)
              .status(AccountStatus.DELETED)
              .build();

      given(loadUserAccountPort.findDeletedByEmail(VALID_EMAIL))
          .willReturn(Optional.of(deletedAccount));

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(UserWithdrawnException.class);

      verifyNoInteractions(
          loadAccountUserInfoPort, passwordEncoder, createAccountUserPort, saveUserAccountPort);
    }

    @Test
    @DisplayName("이메일이 null이면 validate()에서 예외 발생")
    void execute_NullEmail_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(null, VALID_PASSWORD, VALID_NICKNAME, null);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email is required");

      verifyNoInteractions(
          loadAccountUserInfoPort, passwordEncoder, createAccountUserPort, saveUserAccountPort);
    }

    @Test
    @DisplayName("비밀번호가 null이면 validate()에서 예외 발생")
    void execute_NullPassword_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, null, VALID_NICKNAME, null);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Password is required");

      verifyNoInteractions(
          loadAccountUserInfoPort, passwordEncoder, createAccountUserPort, saveUserAccountPort);
    }

    @Test
    @DisplayName("닉네임이 null이면 validate()에서 예외 발생")
    void execute_NullNickname_ThrowsIllegalArgumentException() {
      SignupCommand command = new SignupCommand(VALID_EMAIL, VALID_PASSWORD, null, null);

      assertThatThrownBy(() -> signupService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname is required");

      verifyNoInteractions(
          loadAccountUserInfoPort, passwordEncoder, createAccountUserPort, saveUserAccountPort);
    }
  }
}
