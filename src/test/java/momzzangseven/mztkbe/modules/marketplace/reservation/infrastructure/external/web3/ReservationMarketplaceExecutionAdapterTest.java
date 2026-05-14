package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationMarketplaceExecutionAdapterTest {

  @Mock private PrepareMarketplaceUserExecutionUseCase useCase;

  private ReservationMarketplaceExecutionAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ReservationMarketplaceExecutionAdapter(useCase);
  }

  @Test
  void preparePurchase_mapsActionAndReservationSnapshot() {
    when(useCase.prepare(any())).thenReturn(result("MARKETPLACE_CLASS_PURCHASE"));
    ArgumentCaptor<MarketplaceEscrowExecutionRequest> captor =
        ArgumentCaptor.forClass(MarketplaceEscrowExecutionRequest.class);

    adapter.preparePurchase(command());

    verify(useCase).prepare(captor.capture());
    MarketplaceEscrowExecutionRequest request = captor.getValue();
    assertThat(request.actionType())
        .isEqualTo(MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE);
    assertThat(request.reservationId()).isEqualTo(123L);
    assertThat(request.resourceId()).isEqualTo("123");
    assertThat(request.orderKey()).isEqualTo(MarketplaceEscrowIdCodec.orderKey(123L));
    assertThat(request.requesterUserId()).isEqualTo(7L);
    assertThat(request.buyerUserId()).isEqualTo(7L);
    assertThat(request.trainerUserId()).isEqualTo(9L);
    assertThat(request.reservationVersion()).isEqualTo(3L);
    assertThat(request.buyerWalletAddress()).isEqualTo("0xbuyer");
    assertThat(request.trainerWalletAddress()).isEqualTo("0xtrainer");
    assertThat(request.bookedPriceAmountKrw()).isEqualTo(50000);
    assertThat(request.sessionEndAt()).isEqualTo(sessionEndAt());
  }

  @Test
  void prepareCancel_mapsAction() {
    when(useCase.prepare(any())).thenReturn(result("MARKETPLACE_CLASS_CANCEL"));
    ArgumentCaptor<MarketplaceEscrowExecutionRequest> captor =
        ArgumentCaptor.forClass(MarketplaceEscrowExecutionRequest.class);

    adapter.prepareCancel(command());

    verify(useCase).prepare(captor.capture());
    assertThat(captor.getValue().actionType())
        .isEqualTo(MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL);
  }

  @Test
  void prepareConfirm_mapsAction() {
    when(useCase.prepare(any())).thenReturn(result("MARKETPLACE_CLASS_CONFIRM"));
    ArgumentCaptor<MarketplaceEscrowExecutionRequest> captor =
        ArgumentCaptor.forClass(MarketplaceEscrowExecutionRequest.class);

    adapter.prepareConfirm(command());

    verify(useCase).prepare(captor.capture());
    assertThat(captor.getValue().actionType())
        .isEqualTo(MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM);
  }

  @Test
  void mapsMarketplaceResultToReservationResult() {
    when(useCase.prepare(any())).thenReturn(result("MARKETPLACE_CLASS_PURCHASE"));

    PrepareReservationEscrowResult result = adapter.preparePurchase(command());

    ReservationExecutionWriteView web3 = result.web3();
    assertThat(web3.resource().type()).isEqualTo("ORDER");
    assertThat(web3.resource().id()).isEqualTo("123");
    assertThat(web3.resource().status()).isEqualTo("PENDING_EXECUTION");
    assertThat(web3.actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(web3.executionIntent().id()).isEqualTo("intent-1");
    assertThat(web3.execution().mode()).isEqualTo("EIP7702");
    assertThat(web3.signRequest().authorization().delegateTarget()).isEqualTo("0xdelegate");
    assertThat(web3.signRequest().transaction()).isNull();
    assertThat(web3.signRequestUnavailableReason()).isNull();
    assertThat(web3.existing()).isFalse();
  }

  @Test
  void commandDoesNotCarryOrderKeyOrTokenWeiFields() {
    assertThat(PrepareReservationEscrowCommand.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("orderKey", "tokenAddress", "priceAmountWei")
        .contains("bookedPriceAmountKrw");
  }

  private static PrepareReservationEscrowCommand command() {
    return new PrepareReservationEscrowCommand(
        123L, 7L, 7L, 9L, 3L, "0xbuyer", "0xtrainer", 50000, sessionEndAt());
  }

  private static MarketplaceExecutionIntentResult result(String actionType) {
    return new MarketplaceExecutionIntentResult(
        new MarketplaceExecutionIntentResult.Resource("ORDER", "123", "PENDING_EXECUTION"),
        actionType,
        new MarketplaceExecutionIntentResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.parse("2026-05-20T10:05:00")),
        new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
        signRequest(),
        null,
        false);
  }

  private static MarketplaceSignRequest signRequest() {
    return MarketplaceSignRequest.forEip7702(
        new MarketplaceSignRequest.Authorization(10L, "0xdelegate", 12L, "0xpayload"),
        new MarketplaceSignRequest.Submit("0xdigest", 1_768_224_000L));
  }

  private static LocalDateTime sessionEndAt() {
    return LocalDateTime.parse("2026-05-20T11:00:00");
  }
}
