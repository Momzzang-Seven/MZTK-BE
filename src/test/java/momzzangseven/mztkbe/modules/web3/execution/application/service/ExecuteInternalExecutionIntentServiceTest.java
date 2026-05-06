package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.model.KmsException;

@ExtendWith(MockitoExtension.class)
class ExecuteInternalExecutionIntentServiceTest {

  private static final String SPONSOR_ALIAS = "test-sponsor";
  private static final String SPONSOR_KMS_KEY = "alias/test-sponsor";
  private static final String SPONSOR_ADDRESS = "0x" + "4".repeat(40);

  private static final ExecuteInternalExecutionIntentCommand COMMAND =
      new ExecuteInternalExecutionIntentCommand(List.of(ExecutionActionType.QNA_ADMIN_SETTLE));

  @Mock private ExecuteTransactionalInternalExecutionIntentDelegatePort delegate;
  @Mock private SponsorWalletPreflight sponsorWalletPreflight;

  private ExecuteInternalExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service = new ExecuteInternalExecutionIntentService(delegate, sponsorWalletPreflight);
  }

  @Test
  void execute_callsDelegateWithGateOnPreflightSuccess() {
    SponsorWalletGate gate =
        new SponsorWalletGate(
            new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS, true),
            new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS));
    when(sponsorWalletPreflight.preflight()).thenReturn(gate);
    ExecuteInternalExecutionIntentResult expected = ExecuteInternalExecutionIntentResult.notFound();
    when(delegate.execute(COMMAND, gate)).thenReturn(expected);

    ExecuteInternalExecutionIntentResult result = service.execute(COMMAND);

    assertThat(result).isSameAs(expected);
    verify(delegate).execute(COMMAND, gate);
  }

  @Test
  void execute_returnsPreflightSkippedWhenWalletInvalid_doesNotCallDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new Web3InvalidInputException("sponsor signer key is missing"));

    ExecuteInternalExecutionIntentResult result = service.execute(COMMAND);

    // executed=false → batch loop breaks → no hot-loop on missing wallet.
    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentId()).isNull();
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_returnsPreflightSkippedWhenTreasuryStateRejects_doesNotCallDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new TreasuryWalletStateException("kms key not enabled"));

    ExecuteInternalExecutionIntentResult result = service.execute(COMMAND);

    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    verify(delegate, never()).execute(eq(COMMAND), any());
  }

  @Test
  void execute_returnsPreflightSkippedWhenKmsDescribeFails_doesNotCallDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new KmsKeyDescribeFailedException("KMS DescribeKey failed"));

    ExecuteInternalExecutionIntentResult result = service.execute(COMMAND);

    // Transient KMS failure must not propagate to the cron worker as a per-intent terminal
    // failure — this would inflate failedCount and ERROR-log every throttle hit.
    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    verify(delegate, never()).execute(eq(COMMAND), any());
  }

  @Test
  @DisplayName("terminal KMS DescribeKey (AccessDenied) → preflightSkipped + ERROR 로그 alert anchor")
  void execute_returnsPreflightSkippedOnTerminalKmsDescribe_doesNotCallDelegate() {
    KmsException terminalCause =
        (KmsException)
            KmsException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .build();
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new KmsKeyDescribeFailedException("KMS DescribeKey failed", terminalCause));

    ExecuteInternalExecutionIntentResult result = service.execute(COMMAND);

    // Intent is still NOT claimed — sponsor wallet itself is broken; no specific intent at fault.
    // ERROR-level event=INTERNAL_EXECUTION_PREFLIGHT_TERMINAL_KMS is the alert grep anchor; this
    // test verifies the orchestrator does not propagate or escalate beyond the skip contract.
    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    verify(delegate, never()).execute(eq(COMMAND), any());
  }

  @Test
  @DisplayName(
      "[M-40] @Transactional 가 service 클래스에 부착되지 않음 — TransactionTemplate 은 wrapped delegate 가 보유")
  void serviceClass_isNotAnnotatedWithTransactional() {
    Transactional annotation =
        ExecuteInternalExecutionIntentService.class.getAnnotation(Transactional.class);

    assertThat(annotation)
        .as(
            "ExecuteInternalExecutionIntentService 의 preflight 는 @Transactional 밖에서 실행되어야 한다 — REQUIRES_NEW 는 wrapped delegate bean 이 책임진다")
        .isNull();
  }
}
