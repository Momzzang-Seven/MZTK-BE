package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkExecutionIntentOutcomeServiceTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;

  private MarkExecutionIntentSucceededService succeededService;
  private MarkExecutionIntentFailedOnchainService failedOnchainService;

  @BeforeEach
  void setUp() {
    succeededService =
        new MarkExecutionIntentSucceededService(
            executionIntentPersistencePort, List.of(executionActionHandlerPort), FIXED_CLOCK);
    failedOnchainService =
        new MarkExecutionIntentFailedOnchainService(executionIntentPersistencePort, FIXED_CLOCK);
  }

  @Test
  void markSucceeded_confirmsPendingIntent() {
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(
            argThat(intent -> intent.getSubmittedTxId().equals(12L))))
        .thenReturn(
            new ExecutionActionPlan(
                BigInteger.ZERO,
                ExecutionReferenceType.USER_TO_SERVER,
                List.of(new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x1234"))));
    ExecutionIntent pendingIntent = pendingEip1559Intent();
    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(pendingIntent));
    when(executionIntentPersistencePort.update(
            argThat(updated -> updated.getSubmittedTxId().equals(12L))))
        .thenAnswer(invocation -> invocation.getArgument(0));

    succeededService.execute(12L);

    verify(executionIntentPersistencePort)
        .update(
            argThat(
                updated ->
                    updated.getStatus()
                            == momzzangseven.mztkbe.modules.web3.execution.domain.model
                                .ExecutionIntentStatus.CONFIRMED
                        && updated.getSubmittedTxId().equals(12L)));
    verify(executionActionHandlerPort)
        .afterExecutionConfirmed(
            argThat(
                updated ->
                    updated.getStatus()
                            == momzzangseven.mztkbe.modules.web3.execution.domain.model
                                .ExecutionIntentStatus.CONFIRMED
                        && updated.getSubmittedTxId().equals(12L)),
            argThat(
                plan ->
                    plan.amountWei().compareTo(BigInteger.ZERO) == 0
                        && plan.referenceType() == ExecutionReferenceType.USER_TO_SERVER
                        && plan.calls().size() == 1
                        && "0x1234".equals(plan.calls().get(0).data())));
  }

  @Test
  void markFailedOnchain_marksPendingIntentFailed() {
    ExecutionIntent pendingIntent = pendingEip1559Intent();
    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(pendingIntent));
    when(executionIntentPersistencePort.update(
            argThat(updated -> updated.getSubmittedTxId().equals(12L))))
        .thenAnswer(invocation -> invocation.getArgument(0));

    failedOnchainService.execute(12L, "RECEIPT_STATUS_0");

    verify(executionIntentPersistencePort)
        .update(
            argThat(
                updated ->
                    updated.getStatus()
                            == momzzangseven.mztkbe.modules.web3.execution.domain.model
                                .ExecutionIntentStatus.FAILED_ONCHAIN
                        && "FAILED_ONCHAIN".equals(updated.getLastErrorCode())
                        && "RECEIPT_STATUS_0".equals(updated.getLastErrorReason())));
  }

  @Test
  @DisplayName("핸들러 예외 발생 시에도 CONFIRMED 상태 업데이트가 유지되고 예외가 전파되지 않는다")
  void markSucceeded_handlerThrows_confirmedStateIsPreserved() {
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(any()))
        .thenReturn(
            new ExecutionActionPlan(
                BigInteger.ZERO,
                ExecutionReferenceType.USER_TO_SERVER,
                List.of(new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x1234"))));
    doThrow(new IllegalStateException("missing qna question projection: postId=101"))
        .when(executionActionHandlerPort)
        .afterExecutionConfirmed(any(), any());

    ExecutionIntent pendingIntent = pendingEip1559Intent();
    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(pendingIntent));
    when(executionIntentPersistencePort.update(
            argThat(updated -> updated.getSubmittedTxId().equals(12L))))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatCode(() -> succeededService.execute(12L)).doesNotThrowAnyException();

    verify(executionIntentPersistencePort)
        .update(argThat(updated -> updated.getStatus() == ExecutionIntentStatus.CONFIRMED));
  }

  @Test
  @DisplayName("이미 CONFIRMED 상태인 intent 는 재처리하지 않는다")
  void markSucceeded_alreadyConfirmed_doesNothing() {
    ExecutionIntent confirmedIntent = pendingEip1559Intent().confirm(FIXED_NOW.plusSeconds(3));
    when(executionIntentPersistencePort.findBySubmittedTxIdForUpdate(12L))
        .thenReturn(Optional.of(confirmedIntent));

    succeededService.execute(12L);

    verify(executionIntentPersistencePort, never()).update(any());
    verify(executionActionHandlerPort, never()).afterExecutionConfirmed(any(), any());
  }

  private ExecutionIntent pendingEip1559Intent() {
    return ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            ExecutionResourceType.TRANSFER,
            "transfer:7:ref-1",
            ExecutionActionType.TRANSFER_SEND,
            7L,
            8L,
            ExecutionMode.EIP1559,
            "0x" + "a".repeat(64),
            "{\"payload\":true}",
            null,
            null,
            null,
            FIXED_NOW.plusMinutes(5),
            null,
            null,
            new UnsignedTxSnapshot(
                11155111L,
                "0x" + "1".repeat(40),
                "0x" + "2".repeat(40),
                BigInteger.ZERO,
                "0x1234",
                5L,
                BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L)),
            "0x" + "b".repeat(64),
            BigInteger.ZERO,
            LocalDate.of(2026, 4, 6),
            FIXED_NOW)
        .toBuilder()
        .id(1L)
        .build()
        .markSigned(12L, FIXED_NOW.plusSeconds(1))
        .markPendingOnchain(12L, FIXED_NOW.plusSeconds(2));
  }
}
