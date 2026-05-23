package momzzangseven.mztkbe.modules.web3.marketplace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionProvenanceActor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketplaceEscrowExecutionPayload")
class MarketplaceEscrowExecutionPayloadTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  @Test
  @DisplayName("legacy v1 user payload는 새 admin 필드 없이도 USER source로 역직렬화된다")
  void legacyUserPayload_deserializesWithUserDefaults() throws Exception {
    String json =
        """
        {
          "actionType": "MARKETPLACE_CLASS_CONFIRM",
          "actorType": "BUYER",
          "reservationId": 123,
          "resourceId": "123",
          "orderId": "123e4567-e89b-12d3-a456-426614174000",
          "orderKey": "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
          "authorityUserId": 10,
          "requesterUserId": 10,
          "counterpartyUserId": 20,
          "buyerUserId": 10,
          "trainerUserId": 20,
          "reservationVersion": 1,
          "expectedReservationStatus": "APPROVED",
          "expectedEscrowStatus": "LOCKED",
          "buyerWalletAddress": "0x1111111111111111111111111111111111111111",
          "trainerWalletAddress": "0x2222222222222222222222222222222222222222",
          "tokenAddress": "0x3333333333333333333333333333333333333333",
          "priceBaseUnits": 50000,
          "allowanceStrategy": "PRE_EXISTING_ALLOWANCE",
          "sessionEndAt": "2026-05-20T10:00:00",
          "callTarget": "0x4444444444444444444444444444444444444444",
          "callData": "0x1234",
          "tokenMovement": {
            "tokenAddress": "0x3333333333333333333333333333333333333333",
            "amountBaseUnits": 50000,
            "fromRole": "ESCROW",
            "fromAddress": "0x4444444444444444444444444444444444444444",
            "toRole": "TRAINER",
            "toAddress": "0x2222222222222222222222222222222222222222"
          },
          "payloadVersion": 1
        }
        """;

    MarketplaceEscrowExecutionPayload payload =
        OBJECT_MAPPER.readValue(json, MarketplaceEscrowExecutionPayload.class);

    assertThat(payload.requestSource()).isEqualTo("USER");
    assertThat(payload.operatorUserId()).isNull();
    assertThat(payload.schedulerRunId()).isNull();
    assertThat(payload.reasonCode()).isNull();
    assertThat(payload.memo()).isNull();
  }

  @Test
  @DisplayName("admin payload는 memo를 trim해 저장하고 idempotency view에서는 provenance 필드를 제외한다")
  void adminPayload_idempotencyViewExcludesProvenanceFields() {
    MarketplaceEscrowExecutionPayload first =
        adminPayload(901L, "attempt-1", 77L, "scheduler-1", "  evidence memo  ");
    MarketplaceEscrowExecutionPayload second =
        adminPayload(901L, "attempt-1", 88L, "scheduler-2", "changed memo");

    assertThat(first.memo()).isEqualTo("evidence memo");
    assertThat(first.idempotencyView()).isEqualTo(second.idempotencyView());
  }

  @Test
  @DisplayName("admin payload idempotency view는 hook 매칭용 attempt/actionState 차이를 포함한다")
  void adminPayload_idempotencyViewIncludesHookMatchingAttemptFields() {
    MarketplaceEscrowExecutionPayload first =
        adminPayload(901L, "attempt-1", 77L, "scheduler-1", "memo");
    MarketplaceEscrowExecutionPayload differentAttempt =
        adminPayload(901L, "attempt-2", 77L, "scheduler-1", "memo");
    MarketplaceEscrowExecutionPayload differentActionState =
        adminPayload(902L, "attempt-1", 77L, "scheduler-1", "memo");

    assertThat(first.idempotencyView()).isNotEqualTo(differentAttempt.idempotencyView());
    assertThat(first.idempotencyView()).isNotEqualTo(differentActionState.idempotencyView());
  }

  @Test
  @DisplayName("admin payload idempotency view는 source/reason 차이를 실행 의미 차이로 취급한다")
  void adminPayload_idempotencyViewIncludesSourceAndReason() {
    MarketplaceEscrowExecutionPayload manual =
        adminPayload(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            "MANUAL_ADMIN",
            "ADMIN_MANUAL_SETTLE");
    MarketplaceEscrowExecutionPayload scheduler =
        adminPayload(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            "SCHEDULER",
            "ADMIN_MANUAL_SETTLE");
    MarketplaceEscrowExecutionPayload timeout =
        adminPayload(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            "MANUAL_ADMIN",
            "BUYER_CONFIRMATION_TIMEOUT");

    assertThat(manual.idempotencyView()).isNotEqualTo(scheduler.idempotencyView());
    assertThat(manual.idempotencyView()).isNotEqualTo(timeout.idempotencyView());
  }

  @Test
  @DisplayName("admin payload는 v2와 admin provenance/reason/actionState를 필수로 요구한다")
  void adminPayload_requiresV2AdminFields() {
    assertThatThrownBy(
            () ->
                new MarketplaceEscrowExecutionPayload(
                    MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
                    null,
                    123L,
                    "123",
                    "123e4567-e89b-12d3-a456-426614174000",
                    "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
                    null,
                    10L,
                    20L,
                    10L,
                    20L,
                    1L,
                    "ADMIN_REFUND_PENDING",
                    "ADMIN_REFUND_PENDING",
                    "0x1111111111111111111111111111111111111111",
                    "0x2222222222222222222222222222222222222222",
                    "0x3333333333333333333333333333333333333333",
                    BigInteger.valueOf(50_000),
                    null,
                    LocalDateTime.of(2026, 5, 20, 10, 0),
                    null,
                    null,
                    "attempt-1",
                    "TIMEOUT_CANCELLED",
                    "0x4444444444444444444444444444444444444444",
                    "0x1234",
                    tokenMovement(),
                    null,
                    null,
                    1,
                    900L,
                    901L,
                    "root",
                    MarketplaceAdminExecutionProvenanceActor.ADMIN,
                    "MANUAL_ADMIN",
                    77L,
                    null,
                    "TRAINER_TIMEOUT",
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("payloadVersion");
  }

  private MarketplaceEscrowExecutionPayload adminPayload(
      Long actionStateId,
      String pendingAttemptToken,
      Long operatorUserId,
      String schedulerRunId,
      String memo) {
    return new MarketplaceEscrowExecutionPayload(
        MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
        null,
        123L,
        "123",
        "123e4567-e89b-12d3-a456-426614174000",
        "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
        null,
        10L,
        20L,
        10L,
        20L,
        1L,
        "ADMIN_REFUND_PENDING",
        "ADMIN_REFUND_PENDING",
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222",
        "0x3333333333333333333333333333333333333333",
        BigInteger.valueOf(50_000),
        null,
        LocalDateTime.of(2026, 5, 20, 10, 0),
        null,
        null,
        pendingAttemptToken,
        "TIMEOUT_CANCELLED",
        "0x4444444444444444444444444444444444444444",
        "0x1234",
        tokenMovement(),
        null,
        null,
        2,
        900L,
        actionStateId,
        "root",
        MarketplaceAdminExecutionProvenanceActor.ADMIN,
        "MANUAL_ADMIN",
        operatorUserId,
        schedulerRunId,
        "TRAINER_TIMEOUT",
        memo);
  }

  private MarketplaceEscrowExecutionPayload adminPayload(
      MarketplaceExecutionActionType actionType, String requestSource, String reasonCode) {
    return new MarketplaceEscrowExecutionPayload(
        actionType,
        null,
        123L,
        "123",
        "123e4567-e89b-12d3-a456-426614174000",
        "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
        null,
        10L,
        20L,
        10L,
        20L,
        1L,
        "ADMIN_SETTLE_PENDING",
        "ADMIN_SETTLE_PENDING",
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222",
        "0x3333333333333333333333333333333333333333",
        BigInteger.valueOf(50_000),
        null,
        LocalDateTime.of(2026, 5, 20, 10, 0),
        null,
        null,
        "attempt",
        "AUTO_SETTLED",
        "0x4444444444444444444444444444444444444444",
        "0x1234",
        tokenMovement(),
        null,
        null,
        2,
        900L,
        901L,
        "root",
        MarketplaceAdminExecutionProvenanceActor.ADMIN,
        requestSource,
        77L,
        null,
        reasonCode,
        "memo");
  }

  private static MarketplaceTokenMovement tokenMovement() {
    return new MarketplaceTokenMovement(
        "0x3333333333333333333333333333333333333333",
        BigInteger.valueOf(50_000),
        "ESCROW",
        "0x4444444444444444444444444444444444444444",
        "BUYER",
        "0x1111111111111111111111111111111111111111");
  }
}
