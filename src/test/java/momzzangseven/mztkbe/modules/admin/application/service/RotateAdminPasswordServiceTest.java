package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.admin.AdminAccountNotFoundException;
import momzzangseven.mztkbe.global.error.admin.WeakAdminPasswordException;
import momzzangseven.mztkbe.modules.admin.application.dto.RotateAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RotateAdminPasswordService 단위 테스트")
class RotateAdminPasswordServiceTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;
  @Mock private AdminPasswordEncoderPort adminPasswordEncoderPort;
  @Mock private SaveAdminAccountPort saveAdminAccountPort;

  @InjectMocks private RotateAdminPasswordService service;

  private static final Long USER_ID = 1L;
  private static final String EXISTING_HASH = "$2a$10$existingHash";
  private static final String VALID_NEW_PASSWORD = "NewValidPassword12345!";

  private AdminAccount buildActiveAccount() {
    return AdminAccount.builder()
        .id(1L)
        .userId(USER_ID)
        .loginId("admin001")
        .passwordHash(EXISTING_HASH)
        .createdBy(99L)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .passwordLastRotatedAt(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-134] execute successfully rotates password with valid inputs")
    void execute_validInputs_rotatesPassword() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("currentPass", EXISTING_HASH)).willReturn(true);
      given(adminPasswordEncoderPort.encode(VALID_NEW_PASSWORD)).willReturn("$2a$10$newHash");

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "currentPass", VALID_NEW_PASSWORD);

      // when
      service.execute(command);

      // then
      ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
      verify(saveAdminAccountPort).save(captor.capture());
      org.assertj.core.api.Assertions.assertThat(captor.getValue().getPasswordHash())
          .isEqualTo("$2a$10$newHash");
    }

    @Test
    @DisplayName(
        "[M-143] execute accepts exactly 20-char password that meets all complexity requirements")
    void execute_exactlyTwentyChars_succeeds() {
      // given
      String twentyCharPassword = "Abcdefghij1234567!xY"; // 20 chars
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("current", EXISTING_HASH)).willReturn(true);
      given(adminPasswordEncoderPort.encode(twentyCharPassword)).willReturn("$2a$10$newHash");

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", twentyCharPassword);

      // when
      service.execute(command);

      // then
      verify(saveAdminAccountPort).save(any(AdminAccount.class));
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-135] execute throws AdminAccountNotFoundException when account not found")
    void execute_accountNotFound_throwsAdminAccountNotFoundException() {
      // given
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.empty());

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", VALID_NEW_PASSWORD);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(AdminAccountNotFoundException.class);

      verify(adminPasswordEncoderPort, never()).matches(any(), any());
      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName(
        "[M-136] execute throws InvalidCredentialsException when current password is wrong")
    void execute_wrongCurrentPassword_throwsInvalidCredentialsException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("wrongPass", EXISTING_HASH)).willReturn(false);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "wrongPass", VALID_NEW_PASSWORD);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Current password does not match");

      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-137] execute throws WeakAdminPasswordException when new password is too short")
    void execute_passwordTooShort_throwsWeakAdminPasswordException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", "Short1!aB");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class)
          .hasMessageContaining("at least 20 characters");

      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName(
        "[M-138] execute throws WeakAdminPasswordException when new password has no uppercase"
            + " letter")
    void execute_noUppercase_throwsWeakAdminPasswordException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", "abcdefghijklmnop12345!");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class)
          .hasMessageContaining("uppercase letter");
    }

    @Test
    @DisplayName(
        "[M-139] execute throws WeakAdminPasswordException when new password has no lowercase"
            + " letter")
    void execute_noLowercase_throwsWeakAdminPasswordException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", "ABCDEFGHIJKLMNOP12345!");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class)
          .hasMessageContaining("lowercase letter");
    }

    @Test
    @DisplayName("[M-140] execute throws WeakAdminPasswordException when new password has no digit")
    void execute_noDigit_throwsWeakAdminPasswordException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", "AbcdefghijklmnopQRST!");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class)
          .hasMessageContaining("digit");
    }

    @Test
    @DisplayName(
        "[M-141] execute throws WeakAdminPasswordException when new password has no special"
            + " character")
    void execute_noSpecialChar_throwsWeakAdminPasswordException() {
      // given
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", "AbcdefghijklmnopQRS12");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class)
          .hasMessageContaining("special character");
    }

    @Test
    @DisplayName(
        "[M-142] execute throws WeakAdminPasswordException for exactly 19-char password (boundary)")
    void execute_nineteenChars_throwsWeakAdminPasswordException() {
      // given
      String nineteenCharPassword = "Abcdefghij1234567!x"; // 19 chars
      AdminAccount account = buildActiveAccount();
      given(loadAdminAccountPort.findActiveByUserId(USER_ID)).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches(eq("current"), eq(EXISTING_HASH))).willReturn(true);

      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "current", nineteenCharPassword);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(WeakAdminPasswordException.class);
    }
  }

  @Nested
  @DisplayName("커맨드 검증 케이스")
  class CommandValidationCases {

    @Test
    @DisplayName(
        "[M-144] execute throws IllegalArgumentException when userId is null via"
            + " command.validate()")
    void execute_nullUserId_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(null, "current", VALID_NEW_PASSWORD);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must not be null");

      verify(loadAdminAccountPort, never()).findActiveByUserId(any());
    }

    @Test
    @DisplayName(
        "[M-145] execute throws IllegalArgumentException when currentPassword is blank via"
            + " command.validate()")
    void execute_blankCurrentPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command =
          new RotateAdminPasswordCommand(USER_ID, "", VALID_NEW_PASSWORD);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Current password must not be blank");
    }

    @Test
    @DisplayName(
        "[M-146] execute throws IllegalArgumentException when newPassword is blank via"
            + " command.validate()")
    void execute_blankNewPassword_throwsIllegalArgumentException() {
      // given
      RotateAdminPasswordCommand command = new RotateAdminPasswordCommand(USER_ID, "current", "");

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("New password must not be blank");
    }
  }
}
