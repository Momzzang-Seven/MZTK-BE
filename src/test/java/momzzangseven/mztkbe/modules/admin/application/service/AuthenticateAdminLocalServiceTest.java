package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalResult;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticateAdminLocalService 단위 테스트")
class AuthenticateAdminLocalServiceTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;
  @Mock private AdminPasswordEncoderPort adminPasswordEncoderPort;
  @Mock private SaveAdminAccountPort saveAdminAccountPort;

  @InjectMocks private AuthenticateAdminLocalService service;

  private AdminAccount createAdminAccount(Long userId, String loginId, String passwordHash) {
    return AdminAccount.builder()
        .id(1L)
        .userId(userId)
        .loginId(loginId)
        .passwordHash(passwordHash)
        .createdBy(null)
        .passwordLastRotatedAt(Instant.now())
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("유효한 자격 증명으로 인증하면 userId를 반환한다")
    void execute_ValidCredentials_ReturnsUserId() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("rawPassword", "hashed")).willReturn(true);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willAnswer(inv -> inv.getArgument(0));

      // when
      AuthenticateAdminLocalResult result =
          service.execute(new AuthenticateAdminLocalCommand("admin001", "rawPassword"));

      // then
      assertThat(result.userId()).isEqualTo(1L);
      verify(saveAdminAccountPort, times(1)).save(any(AdminAccount.class));
    }

    @Test
    @DisplayName("인증 성공 시 lastLogin을 갱신한다")
    void execute_Success_UpdatesLastLogin() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("rawPassword", "hashed")).willReturn(true);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willAnswer(inv -> inv.getArgument(0));

      // when
      service.execute(new AuthenticateAdminLocalCommand("admin001", "rawPassword"));

      // then
      verify(saveAdminAccountPort, times(1)).save(any(AdminAccount.class));
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("존재하지 않는 loginId로 인증하면 InvalidCredentialsException을 던진다")
    void execute_AccountNotFound_ThrowsInvalidCredentials() {
      // given
      given(loadAdminAccountPort.findActiveByLoginId("unknown")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> service.execute(new AuthenticateAdminLocalCommand("unknown", "rawPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Invalid admin credentials");

      verify(adminPasswordEncoderPort, never()).matches(any(), any());
      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 InvalidCredentialsException을 던진다")
    void execute_WrongPassword_ThrowsInvalidCredentials() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("wrongPassword", "hashed")).willReturn(false);

      // when & then
      assertThatThrownBy(
              () -> service.execute(new AuthenticateAdminLocalCommand("admin001", "wrongPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Invalid admin credentials");

      verify(saveAdminAccountPort, never()).save(any());
    }
  }
}
