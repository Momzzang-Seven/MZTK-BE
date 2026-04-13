package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.admin.AdminAccountNotFoundException;
import momzzangseven.mztkbe.global.error.admin.SelfResetForbiddenException;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordResult;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
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
@DisplayName("ResetPeerAdminPasswordService 단위 테스트")
class ResetPeerAdminPasswordServiceTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;
  @Mock private GenerateCredentialPort generateCredentialPort;
  @Mock private AdminPasswordEncoderPort adminPasswordEncoderPort;
  @Mock private SaveAdminAccountPort saveAdminAccountPort;

  @InjectMocks private ResetPeerAdminPasswordService service;

  private AdminAccount buildTargetAccount() {
    return AdminAccount.builder()
        .id(2L)
        .userId(2L)
        .loginId("target01")
        .passwordHash("$2a$10$oldHash")
        .createdBy(1L)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .passwordLastRotatedAt(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-147] execute successfully resets peer admin's password")
    void execute_validPeerReset_returnsNewCredentials() {
      // given
      AdminAccount target = buildTargetAccount();
      given(loadAdminAccountPort.findActiveByUserId(2L)).willReturn(Optional.of(target));
      given(generateCredentialPort.generatePasswordOnly()).willReturn("NewGeneratedPass123!x");
      given(adminPasswordEncoderPort.encode("NewGeneratedPass123!x"))
          .willReturn("$2a$10$resetHash");

      ResetPeerAdminPasswordCommand command = new ResetPeerAdminPasswordCommand(1L, 2L);

      // when
      ResetPeerAdminPasswordResult result = service.execute(command);

      // then
      assertThat(result.userId()).isEqualTo(2L);
      assertThat(result.loginId()).isEqualTo("target01");
      assertThat(result.plaintext()).isEqualTo("NewGeneratedPass123!x");
      assertThat(result.resetAt()).isNotNull();

      ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
      verify(saveAdminAccountPort).save(captor.capture());
      assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$resetHash");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-148] execute throws SelfResetForbiddenException when operator equals target")
    void execute_selfReset_throwsSelfResetForbiddenException() {
      // given
      ResetPeerAdminPasswordCommand command = new ResetPeerAdminPasswordCommand(1L, 1L);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(SelfResetForbiddenException.class);

      verify(loadAdminAccountPort, never()).findActiveByUserId(any());
      verify(generateCredentialPort, never()).generatePasswordOnly();
      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName(
        "[M-149] execute throws AdminAccountNotFoundException when target account not found")
    void execute_targetNotFound_throwsAdminAccountNotFoundException() {
      // given
      given(loadAdminAccountPort.findActiveByUserId(999L)).willReturn(Optional.empty());

      ResetPeerAdminPasswordCommand command = new ResetPeerAdminPasswordCommand(1L, 999L);

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(AdminAccountNotFoundException.class);

      verify(generateCredentialPort, never()).generatePasswordOnly();
      verify(saveAdminAccountPort, never()).save(any());
    }
  }
}
