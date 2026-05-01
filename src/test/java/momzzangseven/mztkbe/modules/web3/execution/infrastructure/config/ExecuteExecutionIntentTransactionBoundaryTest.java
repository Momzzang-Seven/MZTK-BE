package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteExecutionIntent 트랜잭션 경계 테스트")
class ExecuteExecutionIntentTransactionBoundaryTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-15T01:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;
  @Mock private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;

  @Test
  @DisplayName("terminal 예외는 상태 변경을 커밋한 뒤 클라이언트 예외로 다시 던진다")
  void execute_commitsTerminalStateBeforeRethrowingTerminalException() {
    // given
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    ExecuteExecutionIntentUseCase useCase = transactionBoundUseCase(transactionManager);
    ExecutionIntent expiredIntent =
        existingEip1559Intent().toBuilder().expiresAt(FIXED_NOW.minusSeconds(1)).build();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(expiredIntent));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(any())).thenReturn(actionPlan());

    // when & then
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned")))
        .isInstanceOf(ExecutionIntentTerminalException.class);

    assertThat(transactionManager.commits).isEqualTo(1);
    assertThat(transactionManager.rollbacks).isZero();
    verify(executionIntentPersistencePort)
        .update(argThat(intent -> intent.getStatus() == ExecutionIntentStatus.EXPIRED));
  }

  @Test
  @DisplayName("일반 validation 예외는 커밋하지 않고 롤백한다")
  void execute_rollsBackValidationException() {
    // given
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    ExecuteExecutionIntentUseCase useCase = transactionBoundUseCase(transactionManager);

    when(executionIntentPersistencePort.findByPublicIdForUpdate("missing-intent"))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "missing-intent", null, null, "0xsigned")))
        .isInstanceOf(Web3InvalidInputException.class);

    assertThat(transactionManager.commits).isZero();
    assertThat(transactionManager.rollbacks).isEqualTo(1);
  }

  private ExecuteExecutionIntentUseCase transactionBoundUseCase(
      RecordingTransactionManager transactionManager) {
    ExecuteExecutionIntentService delegate =
        new ExecuteExecutionIntentService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            executionTransactionGatewayPort,
            loadExecutionSponsorKeyPort,
            executionEip7702GatewayPort,
            eip1559TransactionCodecPort,
            loadExecutionChainIdPort,
            loadExecutionSponsorWalletConfigPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
            FIXED_CLOCK);
    return new ExecutionIntentServiceConfig()
        .executeExecutionIntentUseCase(delegate, transactionManager);
  }

  private ExecutionActionPlan actionPlan() {
    return new ExecutionActionPlan(
        BigInteger.valueOf(100),
        ExecutionReferenceType.USER_TO_USER,
        List.of(new ExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234")));
  }

  private ExecutionIntent existingEip1559Intent() {
    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        ExecutionResourceType.TRANSFER,
        "transfer:1",
        ExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        "{\"amountWei\":\"100\"}",
        null,
        null,
        null,
        FIXED_NOW,
        null,
        null,
        new UnsignedTxSnapshot(
            11155111L,
            "0x" + "4".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L)),
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 15),
        FIXED_NOW);
  }

  private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      // No resources are needed; this test only observes transaction manager decisions.
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
