package momzzangseven.mztkbe.modules.admin.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;
import momzzangseven.mztkbe.modules.admin.application.port.in.BootstrapSeedAdminsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeedAdminBootstrapper 단위 테스트")
class SeedAdminBootstrapperTest {

  @Mock private BootstrapSeedAdminsUseCase bootstrapSeedAdminsUseCase;
  @Mock private RecoveryAnchorPort recoveryAnchorPort;

  @InjectMocks private SeedAdminBootstrapper bootstrapper;

  private static final DefaultApplicationArguments EMPTY_ARGS = new DefaultApplicationArguments();

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-163] run succeeds when anchor is valid and bootstrap creates admins")
    void run_validAnchorAndCreated_succeeds() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("valid-anchor-value");
      given(bootstrapSeedAdminsUseCase.execute())
          .willReturn(new SeedBootstrapOutcome(true, 2, "mztk/admin/bootstrap-delivery"));

      // when & then
      assertThatNoException().isThrownBy(() -> bootstrapper.run(EMPTY_ARGS));
      verify(recoveryAnchorPort).loadAnchor();
      verify(bootstrapSeedAdminsUseCase).execute();
    }

    @Test
    @DisplayName("[M-164] run succeeds when anchor is valid and bootstrap is no-op")
    void run_validAnchorAndNoOp_succeeds() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("valid-anchor-value");
      given(bootstrapSeedAdminsUseCase.execute())
          .willReturn(new SeedBootstrapOutcome(false, 2, null));

      // when & then
      assertThatNoException().isThrownBy(() -> bootstrapper.run(EMPTY_ARGS));
      verify(bootstrapSeedAdminsUseCase).execute();
    }
  }

  @Nested
  @DisplayName("앵커 검증 실패 케이스")
  class AnchorValidationFailureCases {

    @Test
    @DisplayName("[M-165] run throws IllegalStateException when anchor is null")
    void run_nullAnchor_throwsIllegalStateException() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn(null);

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recovery anchor is empty");
      verify(bootstrapSeedAdminsUseCase, never()).execute();
    }

    @Test
    @DisplayName("[M-166] run throws IllegalStateException when anchor is blank")
    void run_blankAnchor_throwsIllegalStateException() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("   ");

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recovery anchor is empty");
      verify(bootstrapSeedAdminsUseCase, never()).execute();
    }

    @Test
    @DisplayName("[M-167] run throws IllegalStateException when anchor is empty string")
    void run_emptyAnchor_throwsIllegalStateException() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("");

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recovery anchor is empty");
      verify(bootstrapSeedAdminsUseCase, never()).execute();
    }

    @Test
    @DisplayName(
        "[M-168] run wraps non-IllegalStateException from anchor port as IllegalStateException")
    void run_anchorPortThrowsRuntimeException_wrapsAsIllegalStateException() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willThrow(new RuntimeException("connection refused"));

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recovery anchor unavailable")
          .hasCauseInstanceOf(RuntimeException.class);
      verify(bootstrapSeedAdminsUseCase, never()).execute();
    }

    @Test
    @DisplayName("[M-169] run re-throws IllegalStateException from anchor port directly")
    void run_anchorPortThrowsIllegalStateException_rethrowsDirectly() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willThrow(new IllegalStateException("custom error"));

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("custom error");
      verify(bootstrapSeedAdminsUseCase, never()).execute();
    }
  }

  @Nested
  @DisplayName("부트스트랩 실행 실패 케이스")
  class BootstrapExecutionFailureCases {

    @Test
    @DisplayName("[M-170] run propagates exception from bootstrap use case")
    void run_useCaseThrows_propagatesException() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("valid-anchor-value");
      given(bootstrapSeedAdminsUseCase.execute())
          .willThrow(new RuntimeException("provisioning failed"));

      // when & then
      assertThatThrownBy(() -> bootstrapper.run(EMPTY_ARGS))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("provisioning failed");
      verify(recoveryAnchorPort).loadAnchor();
    }
  }
}
