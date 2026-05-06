package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
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

/**
 * Tests the orchestrator-level preflight + delegate dispatch contract.
 *
 * <p>Per-mode signing logic lives in {@link TransactionalExecuteExecutionIntentDelegateTest}.
 * Sponsor-wallet structural fail-fast cases live in {@code SponsorWalletPreflightTest}.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteExecutionIntentServiceTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private ExecuteTransactionalExecutionIntentDelegatePort delegate;
  @Mock private SponsorWalletPreflight sponsorWalletPreflight;
  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;

  private ExecuteExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecuteExecutionIntentService(
            delegate,
            sponsorWalletPreflight,
            executionIntentPersistencePort,
            executionTransactionGatewayPort);
  }

  private SponsorWalletGate activeGate() {
    return new SponsorWalletGate(
        new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true),
        new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS));
  }

  private ExecuteExecutionIntentCommand command() {
    return new ExecuteExecutionIntentCommand(7L, "intent-1", "0xauth", "0xsubmit", null);
  }

  @Test
  void execute_propagatesPreflightWeb3InvalidInput_withoutCallingDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new Web3InvalidInputException("sponsor signer key is missing"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_propagatesTreasuryWalletStateException_withoutCallingDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new TreasuryWalletStateException("KMS key disabled"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("KMS key disabled");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_invokesDelegateWithSponsorWalletGate_whenPreflightPasses() {
    SponsorWalletGate gate = activeGate();
    when(sponsorWalletPreflight.preflight()).thenReturn(gate);
    ExecuteExecutionIntentResult expected =
        new ExecuteExecutionIntentResult(
            "intent-1",
            ExecutionIntentStatus.PENDING_ONCHAIN,
            42L,
            ExecutionTransactionStatus.PENDING,
            "0xhash");
    ExecuteExecutionIntentCommand command = command();
    when(delegate.execute(eq(command), eq(gate))).thenReturn(expected);

    ExecuteExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isSameAs(expected);
    verify(delegate).execute(command, gate);
  }

  @Test
  @DisplayName("폴링 패스: submittedTxId != null 이면 preflight 와 delegate 모두 건너뛰고 캐시된 결과 반환")
  void execute_returnsCachedTransaction_whenIntentAlreadySubmitted_skippingPreflight() {
    ExecuteExecutionIntentCommand command = command();
    ExecutionIntent submittedIntent =
        mockSubmittedIntent(command.requesterUserId(), command.executionIntentId(), 42L);
    when(executionIntentPersistencePort.findByPublicId(command.executionIntentId()))
        .thenReturn(Optional.of(submittedIntent));
    when(executionTransactionGatewayPort.findById(42L))
        .thenReturn(
            Optional.of(
                new ExecutionTransactionGatewayPort.TransactionRecord(
                    42L, ExecutionTransactionStatus.PENDING, "0xpastHash")));

    ExecuteExecutionIntentResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(42L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.PENDING);
    assertThat(result.txHash()).isEqualTo("0xpastHash");
    // The whole point: a sponsor wallet that went INACTIVE *after* a successful broadcast must
    // not surface as a 400 to a user merely polling for confirmation status.
    verifyNoInteractions(sponsorWalletPreflight);
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  @DisplayName("폴링 패스의 owner 검증: 다른 사용자가 같은 intent 를 폴링해도 거부")
  void execute_pollingFastPath_rejectsCrossUserAccess() {
    ExecuteExecutionIntentCommand command = command();
    ExecutionIntent submittedIntent = mockSubmittedIntent(99L, command.executionIntentId(), 42L);
    when(executionIntentPersistencePort.findByPublicId(command.executionIntentId()))
        .thenReturn(Optional.of(submittedIntent));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("execution intent owner mismatch");
    verifyNoInteractions(sponsorWalletPreflight);
    verify(delegate, never()).execute(any(), any());
  }

  private ExecutionIntent mockSubmittedIntent(Long requesterUserId, String publicId, Long txId) {
    ExecutionIntent intent = org.mockito.Mockito.mock(ExecutionIntent.class);
    org.mockito.Mockito.lenient().when(intent.getSubmittedTxId()).thenReturn(txId);
    org.mockito.Mockito.lenient().when(intent.getRequesterUserId()).thenReturn(requesterUserId);
    org.mockito.Mockito.lenient().when(intent.getPublicId()).thenReturn(publicId);
    org.mockito.Mockito.lenient()
        .when(intent.getStatus())
        .thenReturn(ExecutionIntentStatus.PENDING_ONCHAIN);
    return intent;
  }

  @Test
  @DisplayName("transient KMS DescribeKey 실패 → Web3TransferException(retryable=true)")
  void execute_translatesTransientDescribeKeyToRetryableWeb3TransferException() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new KmsKeyDescribeFailedException("throttled"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOfSatisfying(
            Web3TransferException.class, ex -> assertThat(ex.isRetryable()).isTrue());
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  @DisplayName("terminal KMS DescribeKey (NotFound) → Web3TransferException(retryable=false)")
  void execute_translatesTerminalDescribeKeyToNonRetryableWeb3TransferException() {
    KmsException terminalCause =
        (KmsException)
            KmsException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NotFoundException").build())
                .build();
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new KmsKeyDescribeFailedException("key missing", terminalCause));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOfSatisfying(
            Web3TransferException.class, ex -> assertThat(ex.isRetryable()).isFalse());
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  @DisplayName("[M-35] @Transactional 가 service 클래스에 부착되지 않음 — 트랜잭션 boundary 는 delegate 가 보유")
  void serviceClass_isNotAnnotatedWithTransactional() {
    Transactional annotation =
        ExecuteExecutionIntentService.class.getAnnotation(Transactional.class);

    assertThat(annotation)
        .as("ExecuteExecutionIntentService 는 preflight 를 @Transactional 밖에서 수행해야 한다")
        .isNull();
  }
}
