package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareTokenTransferExecutionSupportUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PrepareTokenTransferPrevalidationUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionActionType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.web3.QuestionRewardExecutionDraftBuilderAdapter;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardExecutionDraftBuilderTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  @Mock private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;

  @Mock
  private PrepareTokenTransferExecutionSupportUseCase prepareTokenTransferExecutionSupportUseCase;

  @Mock private PrepareTokenTransferPrevalidationUseCase prepareTokenTransferPrevalidationUseCase;
  @Mock private ExecutionPayloadSerializer executionPayloadSerializer;
  @Mock private TransferUnsignedTxFingerprintFactory transferUnsignedTxFingerprintFactory;

  private QuestionRewardExecutionDraftBuilderAdapter builder;

  @BeforeEach
  void setUp() {
    builder =
        new QuestionRewardExecutionDraftBuilderAdapter(
            getActiveWalletAddressUseCase,
            loadTransferRuntimeConfigPort,
            prepareTokenTransferExecutionSupportUseCase,
            prepareTokenTransferPrevalidationUseCase,
            executionPayloadSerializer,
            transferUnsignedTxFingerprintFactory,
            FIXED_CLOCK);
  }

  @Test
  void build_createsQuestionRewardExecutionDraft() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(101L, 201L, 7L, 22L, BigInteger.valueOf(500));
    TransferRuntimeConfig runtimeConfig =
        new TransferRuntimeConfig(
            11155111L,
            "0x" + "3".repeat(40),
            30,
            "0x" + "4".repeat(40),
            "0x" + "5".repeat(40),
            "alias",
            "kek",
            200_000L,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            300,
            "Asia/Seoul",
            7,
            100);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig);
    when(executionPayloadSerializer.serialize(any())).thenReturn("{\"payload\":true}");
    when(executionPayloadSerializer.hashHex(any())).thenReturn("0x" + "a".repeat(64));
    when(transferUnsignedTxFingerprintFactory.compute(any())).thenReturn("0x" + "b".repeat(64));
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.of("0x" + "1".repeat(40)));
    when(getActiveWalletAddressUseCase.execute(22L)).thenReturn(Optional.of("0x" + "2".repeat(40)));
    when(prepareTokenTransferExecutionSupportUseCase.execute(any()))
        .thenReturn(
            new PrepareTokenTransferExecutionSupportResult(9L, "0x" + "a".repeat(64), "0x1234"));
    when(prepareTokenTransferPrevalidationUseCase.execute(any()))
        .thenReturn(
            new PrepareTokenTransferPrevalidationResult(
                true,
                null,
                BigInteger.valueOf(90_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L)));

    TransferExecutionDraft draft = builder.build(command);

    assertThat(draft.resourceType()).isEqualTo(TransferExecutionResourceType.QUESTION);
    assertThat(draft.resourceId()).isEqualTo("101");
    assertThat(draft.resourceStatus()).isEqualTo(TransferExecutionResourceStatus.PENDING_EXECUTION);
    assertThat(draft.actionType()).isEqualTo(TransferExecutionActionType.QNA_ANSWER_ACCEPT);
    assertThat(draft.rootIdempotencyKey()).isEqualTo("domain:QUESTION_REWARD:101:7");
    assertThat(draft.authorityAddress()).isEqualTo("0x" + "1".repeat(40));
    assertThat(draft.counterpartyUserId()).isEqualTo(22L);
    assertThat(draft.unsignedTxSnapshot().toAddress()).isEqualTo("0x" + "3".repeat(40));
    assertThat(draft.expiresAt())
        .isEqualTo(FIXED_NOW.plusSeconds(runtimeConfig.authorizationTtlSeconds()));
  }
}
