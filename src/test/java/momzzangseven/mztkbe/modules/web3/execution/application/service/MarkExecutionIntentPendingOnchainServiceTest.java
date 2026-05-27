package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkExecutionIntentPendingOnchainServiceTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private RunAfterCommitPort runAfterCommitPort;

  private MarkExecutionIntentPendingOnchainService service;

  @BeforeEach
  void setUp() {
    service =
        new MarkExecutionIntentPendingOnchainService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            List.of(),
            runAfterCommitPort,
            FIXED_CLOCK);
  }

  @Test
  void execute_marksSignedIntentPendingAndConsumesReservedExposure() {
    ExecutionIntent intent = signedEip7702Intent();
    LocalDate usageDate = LocalDate.of(2026, 4, 5);
    SponsorDailyUsage usage = SponsorDailyUsage.create(7L, usageDate).reserve(BigInteger.TEN);

    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(intent));
    when(sponsorDailyUsagePersistencePort.getOrCreateForUpdate(7L, usageDate)).thenReturn(usage);
    when(executionIntentPersistencePort.update(
            argThat(updated -> updated.getSubmittedTxId().equals(12L))))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.update(
            argThat(updated -> updated.getConsumedCostWei().compareTo(BigInteger.TEN) == 0)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(12L);

    verify(executionIntentPersistencePort)
        .update(
            argThat(
                updated ->
                    updated.getStatus()
                            == momzzangseven.mztkbe.modules.web3.execution.domain.model
                                .ExecutionIntentStatus.PENDING_ONCHAIN
                        && updated.getSubmittedTxId().equals(12L)));
    verify(sponsorDailyUsagePersistencePort)
        .update(
            argThat(
                updated -> {
                  assertThat(updated.getReservedCostWei()).isEqualByComparingTo(BigInteger.ZERO);
                  assertThat(updated.getConsumedCostWei()).isEqualByComparingTo(BigInteger.TEN);
                  return true;
                }));
  }

  @Test
  void execute_runsSubmissionHookAfterIntentBecomesPending() {
    ExecutionIntent intent = signedEip7702Intent();
    LocalDate usageDate = LocalDate.of(2026, 4, 5);
    SponsorDailyUsage usage = SponsorDailyUsage.create(7L, usageDate).reserve(BigInteger.TEN);
    ExecutionActionPlan actionPlan =
        new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.SERVER_TO_USER, List.of());

    service =
        new MarkExecutionIntentPendingOnchainService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            List.of(executionActionHandlerPort),
            runAfterCommitPort,
            FIXED_CLOCK);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(runAfterCommitPort)
        .runAfterCommit(any(Runnable.class));
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.supports(any(ExecutionIntent.class))).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(any(ExecutionIntent.class)))
        .thenReturn(actionPlan);
    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(intent));
    when(sponsorDailyUsagePersistencePort.getOrCreateForUpdate(7L, usageDate)).thenReturn(usage);
    when(executionIntentPersistencePort.update(
            argThat(updated -> updated.getSubmittedTxId().equals(12L))))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.update(any(SponsorDailyUsage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(12L);

    verify(executionActionHandlerPort)
        .afterTransactionSubmitted(
            argThat(intentArg -> intentArg.getStatus() == ExecutionIntentStatus.PENDING_ONCHAIN),
            eq(actionPlan),
            eq(ExecutionTransactionStatus.PENDING));
  }

  private ExecutionIntent signedEip7702Intent() {
    return ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            ExecutionResourceType.TRANSFER,
            "transfer:7:ref-1",
            ExecutionActionType.TRANSFER_SEND,
            7L,
            8L,
            ExecutionMode.EIP7702,
            "0x" + "a".repeat(64),
            "{\"payload\":true}",
            "0x" + "1".repeat(40),
            3L,
            "0x" + "2".repeat(40),
            FIXED_NOW.plusMinutes(5),
            "0x" + "b".repeat(64),
            "0x" + "c".repeat(64),
            null,
            null,
            BigInteger.TEN,
            LocalDate.of(2026, 4, 5),
            FIXED_NOW)
        .toBuilder()
        .id(1L)
        .build()
        .markSigned(12L, FIXED_NOW.plusSeconds(1));
  }
}
