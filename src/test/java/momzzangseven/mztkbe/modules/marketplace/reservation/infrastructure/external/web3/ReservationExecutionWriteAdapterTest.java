package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentStateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExecutionWriteAdapterTest {

  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;
  @Mock private GetExecutionIntentStateUseCase getExecutionIntentStateUseCase;
  @Mock private ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase;

  private ObjectMapper objectMapper;
  private ReservationExecutionWriteAdapter adapter;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    adapter =
        new ReservationExecutionWriteAdapter(
            getExecutionIntentUseCase,
            getExecutionIntentStateUseCase,
            replayConfirmedExecutionIntentUseCase,
            objectMapper);
  }

  @Test
  void load_restoresSignatureMetaAndTokenMovementFromPayloadSnapshot() throws Exception {
    MarketplaceEscrowExecutionPayload payload = payload();
    when(getExecutionIntentUseCase.execute(new GetExecutionIntentQuery(7L, "intent-public-1")))
        .thenReturn(result(objectMapper.writeValueAsString(payload)));

    ReservationExecutionWriteView view = adapter.load(7L, "intent-public-1");

    assertThat(view.signatureMeta())
        .isEqualTo(
            new ReservationExecutionWriteView.SignatureMeta(payload.signedAt(), 1_700_000_600L));
    assertThat(view.tokenMovement())
        .isEqualTo(
            new ReservationExecutionWriteView.TokenMovement(
                "0xtoken", "50000000000000000000", "BUYER", "0xbuyer", "ESCROW", "0xescrow"));
    assertThat(view.signRequest().submit().deadlineEpochSeconds()).isEqualTo(1_700_000_600L);
  }

  @Test
  void loadState_mapsSubmittedTransactionSummary() {
    when(getExecutionIntentStateUseCase.execute(
            new GetExecutionIntentStateQuery("intent-public-1")))
        .thenReturn(
            new GetExecutionIntentStateResult(
                "intent-public-1",
                ExecutionIntentStatus.PENDING_ONCHAIN,
                ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                7L,
                99L,
                ExecutionTransactionStatus.SUCCEEDED,
                "0xhash"));

    var state = adapter.loadState("intent-public-1");

    assertThat(state.status()).isEqualTo("PENDING_ONCHAIN");
    assertThat(state.transactionId()).isEqualTo(99L);
    assertThat(state.transactionStatus()).isEqualTo("SUCCEEDED");
    assertThat(state.txHash()).isEqualTo("0xhash");
  }

  private GetExecutionIntentResult result(String payloadSnapshotJson) {
    return new GetExecutionIntentResult(
        ExecutionResourceType.ORDER,
        "123",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        "0xpayloadhash",
        payloadSnapshotJson,
        "intent-public-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        LocalDateTime.parse("2023-11-14T22:23:20"),
        1_700_000_600L,
        ExecutionMode.EIP7702,
        2,
        SignRequestBundle.forEip7702(
            new SignRequestBundle.AuthorizationSignRequest(10L, "0xdelegate", 3L, "0xauth"),
            new SignRequestBundle.SubmitSignRequest("0xdigest", 1_700_000_600L)),
        null,
        null,
        null,
        null);
  }

  private MarketplaceEscrowExecutionPayload payload() {
    return new MarketplaceEscrowExecutionPayload(
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        MarketplaceActorType.BUYER,
        123L,
        "123",
        "123e4567-e89b-12d3-a456-426614174000",
        "0x" + "1".repeat(64),
        7L,
        7L,
        9L,
        7L,
        9L,
        3L,
        "PURCHASE_PREPARING",
        "PURCHASE_PREPARING",
        "0xbuyer",
        "0xtrainer",
        "0xtoken",
        new BigInteger("50000000000000000000"),
        MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
        LocalDateTime.parse("2025-06-10T11:00:00"),
        1_750_000_000L,
        1_750_000_000L,
        "attempt-token",
        "PENDING",
        "0xescrow",
        "0xcalldata",
        new MarketplaceTokenMovement(
            "0xtoken",
            new BigInteger("50000000000000000000"),
            "BUYER",
            "0xbuyer",
            "ESCROW",
            "0xescrow"),
        1_700_000_000L,
        "0xsig");
  }
}
