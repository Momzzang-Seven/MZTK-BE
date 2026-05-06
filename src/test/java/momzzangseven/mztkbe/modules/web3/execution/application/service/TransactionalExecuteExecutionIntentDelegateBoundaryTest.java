package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Runtime verification of {@link TransactionalExecuteExecutionIntentDelegate}'s
 * {@code @Transactional(noRollbackFor = ExecutionIntentTerminalException.class)} boundary
 * semantics.
 *
 * <p>Recovers the assertions previously held by {@code
 * ExecuteExecutionIntentTransactionBoundaryTest} (which targeted the now-removed {@code
 * TransactionTemplate}-wrapper pattern). Wraps the delegate in a Spring AOP proxy backed by a
 * recording {@link AbstractPlatformTransactionManager} so we observe commit / rollback decisions
 * without needing a Spring context.
 *
 * <p>The MOM-397 invariant under test: terminal {@link ExecutionIntentTerminalException} commits
 * the upstream EXPIRED / NONCE_STALE state transition before the exception surfaces to the client,
 * and ordinary validation exceptions roll back unconditionally.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalExecuteExecutionIntentDelegate transaction-boundary tests")
class TransactionalExecuteExecutionIntentDelegateBoundaryTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-15T01:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;

  private TransactionalExecuteExecutionIntentDelegate rawDelegate;

  @BeforeEach
  void setUp() {
    rawDelegate =
        new TransactionalExecuteExecutionIntentDelegate(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            executionTransactionGatewayPort,
            executionEip7702GatewayPort,
            eip1559TransactionCodecPort,
            loadExecutionChainIdPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
            publishExecutionIntentTerminatedPort,
            FIXED_CLOCK);
    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND))
        .thenReturn(true);
    lenient()
        .when(executionActionHandlerPort.buildActionPlan(any()))
        .thenReturn(
            new ExecutionActionPlan(
                BigInteger.valueOf(100),
                ExecutionReferenceType.USER_TO_USER,
                List.of(new ExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234"))));
    lenient()
        .when(loadExecutionRetryPolicyPort.loadRetryPolicy())
        .thenReturn(new ExecutionRetryPolicy(30));
    lenient().when(loadExecutionChainIdPort.loadChainId()).thenReturn(11155111L);
  }

  @Test
  @DisplayName("terminal 예외(EXPIRED)는 상태 변경을 커밋한 뒤 다시 던진다 — MOM-397 invariant")
  void terminalException_commitsStateBeforeRethrow() {
    RecordingTransactionManager tm = new RecordingTransactionManager();
    ExecuteTransactionalExecutionIntentDelegatePort proxied = wrapWithTransactionProxy(tm);
    ExecutionIntent expired =
        existingEip1559Intent().toBuilder().expiresAt(FIXED_NOW.minusSeconds(1)).build();
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(expired));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                proxied.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class);

    assertThat(tm.commits)
        .as("noRollbackFor=ExecutionIntentTerminalException — EXPIRED state must be committed")
        .isEqualTo(1);
    assertThat(tm.rollbacks).isZero();
  }

  @Test
  @DisplayName("validation 예외(intent not found)는 커밋하지 않고 롤백한다")
  void validationException_rollsBack() {
    RecordingTransactionManager tm = new RecordingTransactionManager();
    ExecuteTransactionalExecutionIntentDelegatePort proxied = wrapWithTransactionProxy(tm);
    when(executionIntentPersistencePort.findByPublicIdForUpdate("missing-intent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                proxied.execute(
                    new ExecuteExecutionIntentCommand(7L, "missing-intent", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(Web3InvalidInputException.class);

    assertThat(tm.commits).isZero();
    assertThat(tm.rollbacks)
        .as("Generic Web3InvalidInputException must roll back; only terminal is whitelisted")
        .isEqualTo(1);
  }

  private ExecuteTransactionalExecutionIntentDelegatePort wrapWithTransactionProxy(
      AbstractPlatformTransactionManager tm) {
    TransactionInterceptor interceptor =
        new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource());
    ProxyFactory proxyFactory = new ProxyFactory(rawDelegate);
    proxyFactory.addAdvice(interceptor);
    proxyFactory.setProxyTargetClass(false);
    return (ExecuteTransactionalExecutionIntentDelegatePort) proxyFactory.getProxy();
  }

  private SponsorWalletGate sponsorGate() {
    TreasuryWalletInfo info =
        new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
    TreasurySigner signer = new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS);
    return new SponsorWalletGate(info, signer);
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

    int commits;
    int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      // No DB resources are needed; the test only observes commit/rollback decisions.
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
