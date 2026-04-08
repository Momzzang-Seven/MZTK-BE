package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateExecutionIntentServiceTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);
  private static final LocalDate FIXED_DATE = FIXED_NOW.toLocalDate();

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private LoadSponsorPolicyPort loadSponsorPolicyPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadEip1559TtlPort loadEip1559TtlPort;
  @Mock private BuildExecutionDigestPort buildExecutionDigestPort;

  private CreateExecutionIntentService service;
  private ExecutionModeSelector executionModeSelector;

  @BeforeEach
  void setUp() {
    executionModeSelector =
        new ExecutionModeSelector(
            loadSponsorPolicyPort, sponsorDailyUsagePersistencePort, FIXED_CLOCK);
    service =
        new CreateExecutionIntentService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            loadExecutionChainIdPort,
            loadSponsorPolicyPort,
            loadEip1559TtlPort,
            buildExecutionDigestPort,
            executionModeSelector,
            FIXED_CLOCK);
  }

  @Test
  void execute_createsEip7702Intent_whenSponsorEligible() {
    when(loadSponsorPolicyPort.loadSponsorPolicy())
        .thenReturn(
            new SponsorPolicy(
                true, 500_000L, 60L, 2L, new BigDecimal("0.05"), new BigDecimal("1")));
    when(sponsorDailyUsagePersistencePort.find(7L, FIXED_DATE))
        .thenReturn(Optional.of(SponsorDailyUsage.create(7L, FIXED_DATE)));
    when(sponsorDailyUsagePersistencePort.getOrCreateForUpdate(7L, FIXED_DATE))
        .thenReturn(SponsorDailyUsage.create(7L, FIXED_DATE));
    when(buildExecutionDigestPort.buildExecutionDigestHex(any(), any(), any(), any()))
        .thenReturn("0x" + "d".repeat(64));
    when(loadExecutionChainIdPort.loadChainId()).thenReturn(11155111L);
    when(executionIntentPersistencePort.create(any()))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
    when(sponsorDailyUsagePersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CreateExecutionIntentResult result =
        service.execute(new CreateExecutionIntentCommand(transferDraft(true)));

    assertThat(result.mode()).isEqualTo(ExecutionMode.EIP7702);
    assertThat(result.signCount()).isEqualTo(2);
    assertThat(result.signRequest().authorization()).isNotNull();
    assertThat(result.signRequest().submit()).isNotNull();
    verify(buildExecutionDigestPort).buildExecutionDigestHex(any(), any(), any(), any());
    verify(sponsorDailyUsagePersistencePort).update(any());
    verify(executionIntentPersistencePort)
        .create(argThat(intent -> intent.getSponsorUsageDateKst().equals(FIXED_DATE)));
  }

  @Test
  void execute_fallsBackToEip1559_whenSponsorIneligible() {
    lenient().when(loadEip1559TtlPort.loadTtlSeconds()).thenReturn(90L);
    when(loadSponsorPolicyPort.loadSponsorPolicy())
        .thenReturn(
            new SponsorPolicy(
                true, 500_000L, 60L, 2L, new BigDecimal("0.0000001"), new BigDecimal("0.0000001")));
    when(executionIntentPersistencePort.create(any()))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

    CreateExecutionIntentResult result =
        service.execute(new CreateExecutionIntentCommand(transferDraft(true)));

    assertThat(result.mode()).isEqualTo(ExecutionMode.EIP1559);
    assertThat(result.signCount()).isEqualTo(1);
    assertThat(result.signRequest().transaction()).isNotNull();
    assertThat(result.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(90));
  }

  @Test
  void execute_reusesExistingAwaitingSignatureIntent_whenPayloadMatches() {
    ExecutionIntent existing =
        withId(
            ExecutionIntent.create(
                "intent-existing",
                "root-transfer-1",
                1,
                ExecutionResourceType.TRANSFER,
                "web3:TRANSFER_SEND:7:req-1",
                ExecutionActionType.TRANSFER_SEND,
                7L,
                8L,
                ExecutionMode.EIP1559,
                "0x" + "a".repeat(64),
                "{\"payload\":true}",
                null,
                null,
                null,
                FIXED_NOW.plusSeconds(60),
                null,
                null,
                unsignedTxSnapshot(),
                "0x" + "b".repeat(64),
                BigInteger.ZERO,
                FIXED_DATE,
                FIXED_NOW),
            77L);
    when(executionIntentPersistencePort.findLatestByRootIdempotencyKeyForUpdate("root-transfer-1"))
        .thenReturn(Optional.of(existing));

    CreateExecutionIntentResult result =
        service.execute(new CreateExecutionIntentCommand(transferDraft(false)));

    assertThat(result.executionIntentId()).isEqualTo("intent-existing");
    assertThat(result.existing()).isTrue();
  }

  private ExecutionDraft transferDraft(boolean differentPayloadHash) {
    return new ExecutionDraft(
        ExecutionResourceTypeCode.TRANSFER,
        "web3:TRANSFER_SEND:7:req-1",
        ExecutionResourceStatusCode.PENDING_EXECUTION,
        ExecutionActionTypeCode.TRANSFER_SEND,
        7L,
        8L,
        "root-transfer-1",
        differentPayloadHash ? "0x" + "c".repeat(64) : "0x" + "a".repeat(64),
        "{\"payload\":true}",
        java.util.List.of(
            new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x" + "2".repeat(8))),
        true,
        "0x" + "3".repeat(40),
        12L,
        "0x" + "4".repeat(40),
        "0x" + "5".repeat(64),
        unsignedTxSnapshot(),
        "0x" + "b".repeat(64),
        FIXED_NOW.plusSeconds(300));
  }

  private UnsignedTxSnapshot unsignedTxSnapshot() {
    return new UnsignedTxSnapshot(
        11155111L,
        "0x" + "6".repeat(40),
        "0x" + "7".repeat(40),
        BigInteger.ZERO,
        "0x1234",
        5L,
        BigInteger.valueOf(80_000),
        BigInteger.valueOf(2_000_000_000L),
        BigInteger.valueOf(50_000_000_000L));
  }

  private ExecutionIntent withId(ExecutionIntent executionIntent, Long id) {
    return executionIntent.toBuilder().id(id).build();
  }
}
