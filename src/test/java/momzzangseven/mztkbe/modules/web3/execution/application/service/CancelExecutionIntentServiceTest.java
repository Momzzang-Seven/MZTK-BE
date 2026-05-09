package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelExecutionIntentServiceTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);
  private static final LocalDate USAGE_DATE = LocalDate.of(2026, 4, 7);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;

  private CancelExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new CancelExecutionIntentService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            publishExecutionIntentTerminatedPort,
            FIXED_CLOCK);
  }

  @Test
  void cancelIfSignable_returnsFalse_whenIntentIsAbsent() {
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.empty());

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isFalse();
    verify(executionIntentPersistencePort, never()).update(any(ExecutionIntent.class));
    verifyNoInteractions(sponsorDailyUsagePersistencePort, publishExecutionIntentTerminatedPort);
  }

  @Test
  void cancelIfSignable_returnsFalse_whenIntentIsNotSignable() {
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent(ExecutionIntentStatus.SIGNED, BigInteger.TEN)));

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isFalse();
    verify(executionIntentPersistencePort, never()).update(any(ExecutionIntent.class));
    verifyNoInteractions(sponsorDailyUsagePersistencePort, publishExecutionIntentTerminatedPort);
  }

  @Test
  void cancelIfSignable_cancelsWithoutSponsorLookup_whenReservedSponsorCostIsNull() {
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent(ExecutionIntentStatus.AWAITING_SIGNATURE, null)));
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isTrue();
    verifyCancelUpdateAndEvent();
    verifyNoInteractions(sponsorDailyUsagePersistencePort);
  }

  @Test
  void cancelIfSignable_cancelsWithoutSponsorLookup_whenReservedSponsorCostIsNotPositive() {
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent(ExecutionIntentStatus.AWAITING_SIGNATURE, BigInteger.ZERO)));
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isTrue();
    verifyCancelUpdateAndEvent();
    verifyNoInteractions(sponsorDailyUsagePersistencePort);
  }

  @Test
  void cancelIfSignable_cancelsWithoutSponsorRelease_whenSponsorUsageIsAbsent() {
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent(ExecutionIntentStatus.AWAITING_SIGNATURE, BigInteger.TEN)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(7L, USAGE_DATE))
        .thenReturn(Optional.empty());
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isTrue();
    verify(sponsorDailyUsagePersistencePort).findForUpdate(7L, USAGE_DATE);
    verify(sponsorDailyUsagePersistencePort, never()).update(any(SponsorDailyUsage.class));
    verifyCancelUpdateAndEvent();
  }

  @Test
  void cancelIfSignable_releasesReservedSponsorExposure_whenSponsorUsageExists() {
    SponsorDailyUsage usage =
        SponsorDailyUsage.create(7L, USAGE_DATE).reserve(new BigInteger("100"));

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(
            Optional.of(intent(ExecutionIntentStatus.AWAITING_SIGNATURE, new BigInteger("40"))));
    when(sponsorDailyUsagePersistencePort.findForUpdate(7L, USAGE_DATE))
        .thenReturn(Optional.of(usage));
    when(sponsorDailyUsagePersistencePort.update(any(SponsorDailyUsage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    boolean result = service.cancelIfSignable(command());

    assertThat(result).isTrue();
    verify(sponsorDailyUsagePersistencePort)
        .update(
            argThat(
                updated ->
                    updated.getReservedCostWei().compareTo(new BigInteger("60")) == 0
                        && updated.getConsumedCostWei().compareTo(BigInteger.ZERO) == 0));
    verifyCancelUpdateAndEvent();
  }

  private CancelExecutionIntentCommand command() {
    return new CancelExecutionIntentCommand("intent-1", "ANSWER_014", "bind failed");
  }

  private void verifyCancelUpdateAndEvent() {
    verify(executionIntentPersistencePort)
        .update(
            argThat(
                updated ->
                    updated.getStatus() == ExecutionIntentStatus.CANCELED
                        && updated.getUpdatedAt().equals(FIXED_NOW)
                        && "ANSWER_014".equals(updated.getLastErrorCode())
                        && "bind failed".equals(updated.getLastErrorReason())));
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            argThat(
                event ->
                    event.executionIntentId().equals("intent-1")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED
                        && event.failureReason().equals("ANSWER_014")));
  }

  private ExecutionIntent intent(ExecutionIntentStatus status, BigInteger reservedSponsorCostWei) {
    return ExecutionIntent.builder()
        .id(1L)
        .publicId("intent-1")
        .rootIdempotencyKey("root-1")
        .attemptNo(1)
        .resourceType(ExecutionResourceType.TRANSFER)
        .resourceId("transfer:7:ref-1")
        .actionType(ExecutionActionType.TRANSFER_SEND)
        .requesterUserId(7L)
        .counterpartyUserId(8L)
        .mode(ExecutionMode.EIP7702)
        .status(status)
        .payloadHash("0x" + "a".repeat(64))
        .payloadSnapshotJson("{}")
        .authorityAddress("0x" + "1".repeat(40))
        .authorityNonce(3L)
        .delegateTarget("0x" + "2".repeat(40))
        .expiresAt(FIXED_NOW.plusMinutes(5))
        .authorizationPayloadHash("0x" + "b".repeat(64))
        .executionDigest("0x" + "c".repeat(64))
        .reservedSponsorCostWei(reservedSponsorCostWei)
        .sponsorUsageDateKst(USAGE_DATE)
        .createdAt(FIXED_NOW.minusMinutes(1))
        .updatedAt(FIXED_NOW.minusMinutes(1))
        .build();
  }
}
