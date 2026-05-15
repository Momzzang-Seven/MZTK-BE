package momzzangseven.mztkbe.modules.web3.marketplace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignRequest;
import org.junit.jupiter.api.Test;

class MarketplaceExecutionIntentResultTest {

  @Test
  void resultPreservesMarketplaceOwnedShape() {
    MarketplaceSignRequest signRequest = eip7702SignRequest();

    MarketplaceExecutionIntentResult result =
        new MarketplaceExecutionIntentResult(
            new MarketplaceExecutionIntentResult.Resource("ORDER", "123", "PENDING_EXECUTION"),
            "MARKETPLACE_CLASS_PURCHASE",
            new MarketplaceExecutionIntentResult.ExecutionIntent(
                "intent-1", "AWAITING_SIGNATURE", expiresAt()),
            new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
            signRequest,
            null,
            false);

    assertThat(result.resource().type()).isEqualTo("ORDER");
    assertThat(result.resource().id()).isEqualTo("123");
    assertThat(result.actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(result.executionIntent().status()).isEqualTo("AWAITING_SIGNATURE");
    assertThat(result.execution().mode()).isEqualTo("EIP7702");
    assertThat(result.signRequest()).isSameAs(signRequest);
    assertThat(result.signRequestUnavailableReason()).isNull();
    assertThat(result.existing()).isFalse();
  }

  @Test
  void resourceId_isReservationIdString_notOrderKey() {
    assertThat(
            new MarketplaceExecutionIntentResult.Resource("ORDER", "123", "PENDING_EXECUTION").id())
        .isEqualTo("123")
        .doesNotStartWith("0x");
  }

  @Test
  void signRequestSupportsEip7702WithNullTransaction() {
    MarketplaceSignRequest request = eip7702SignRequest();

    assertThat(request.authorization()).isNotNull();
    assertThat(request.submit()).isNotNull();
    assertThat(request.transaction()).isNull();
  }

  @Test
  void signRequestSupportsEip1559TransactionOnly() {
    MarketplaceSignRequest request =
        MarketplaceSignRequest.forEip1559(transaction("0x0", "0x5208", "0x1", "0x2"));

    assertThat(request.authorization()).isNull();
    assertThat(request.submit()).isNull();
    assertThat(request.transaction()).isNotNull();
  }

  @Test
  void signRequestRejectsMalformedTransactionHexQuantity() {
    assertThatThrownBy(
            () ->
                MarketplaceSignRequest.forEip1559(transaction("0x0", "0x5208", "0xnot-hex", "0x2")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGasHex");
  }

  @Test
  void signRequestRejectsMaxFeeBelowPriorityFee() {
    assertThatThrownBy(
            () -> MarketplaceSignRequest.forEip1559(transaction("0x0", "0x5208", "0x2", "0x1")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGasHex");
  }

  @Test
  void signRequestAndUnavailableReasonCannotBothExist() {
    assertThatThrownBy(
            () ->
                new MarketplaceExecutionIntentResult(
                    new MarketplaceExecutionIntentResult.Resource(
                        "ORDER", "123", "PENDING_EXECUTION"),
                    "MARKETPLACE_CLASS_PURCHASE",
                    new MarketplaceExecutionIntentResult.ExecutionIntent(
                        "intent-1", "AWAITING_SIGNATURE", expiresAt()),
                    new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
                    eip7702SignRequest(),
                    "KMS_UNAVAILABLE",
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("cannot both be present");
  }

  @Test
  void awaitingSignatureRequiresSignRequest() {
    assertThatThrownBy(
            () ->
                new MarketplaceExecutionIntentResult(
                    new MarketplaceExecutionIntentResult.Resource(
                        "ORDER", "123", "PENDING_EXECUTION"),
                    "MARKETPLACE_CLASS_PURCHASE",
                    new MarketplaceExecutionIntentResult.ExecutionIntent(
                        "intent-1", "AWAITING_SIGNATURE", expiresAt()),
                    new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
                    null,
                    null,
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signRequest");
  }

  @Test
  void awaitingSignatureWithOnlyUnavailableReasonIsInvalid() {
    assertThatThrownBy(
            () ->
                new MarketplaceExecutionIntentResult(
                    new MarketplaceExecutionIntentResult.Resource(
                        "ORDER", "123", "PENDING_EXECUTION"),
                    "MARKETPLACE_CLASS_PURCHASE",
                    new MarketplaceExecutionIntentResult.ExecutionIntent(
                        "intent-1", "AWAITING_SIGNATURE", expiresAt()),
                    new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
                    null,
                    "KMS_UNAVAILABLE",
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signRequest");
  }

  @Test
  void terminalStatusMayExposeUnavailableReasonWithoutSignRequest() {
    MarketplaceExecutionIntentResult result =
        new MarketplaceExecutionIntentResult(
            new MarketplaceExecutionIntentResult.Resource("ORDER", "123", "FAILED"),
            "MARKETPLACE_CLASS_PURCHASE",
            new MarketplaceExecutionIntentResult.ExecutionIntent(
                "intent-1", "EXPIRED", expiresAt()),
            new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
            null,
            "EXPIRED",
            true);

    assertThat(result.signRequest()).isNull();
    assertThat(result.signRequestUnavailableReason()).isEqualTo("EXPIRED");
  }

  @Test
  void signRequestRejectsPartialEip7702Path() {
    assertThatThrownBy(
            () ->
                new MarketplaceSignRequest(
                    new MarketplaceSignRequest.Authorization(10L, "0xdelegate", 12L, "0xpayload"),
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("provided together");
  }

  @Test
  void resultRejectsMissingRequiredFields() {
    assertThatThrownBy(
            () ->
                new MarketplaceExecutionIntentResult(
                    null,
                    "MARKETPLACE_CLASS_PURCHASE",
                    new MarketplaceExecutionIntentResult.ExecutionIntent(
                        "intent-1", "AWAITING_SIGNATURE", expiresAt()),
                    new MarketplaceExecutionIntentResult.Execution("EIP7702", 2),
                    eip7702SignRequest(),
                    null,
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("resource");
  }

  private static MarketplaceSignRequest eip7702SignRequest() {
    return MarketplaceSignRequest.forEip7702(
        new MarketplaceSignRequest.Authorization(10L, "0xdelegate", 12L, "0xpayload"),
        new MarketplaceSignRequest.Submit("0xdigest", 1_768_224_000L));
  }

  private static MarketplaceSignRequest.Transaction transaction(
      String valueHex, String gasLimitHex, String maxPriorityFeePerGasHex, String maxFeePerGasHex) {
    return new MarketplaceSignRequest.Transaction(
        10L,
        "0xfrom",
        "0xto",
        valueHex,
        "0xdata",
        1L,
        gasLimitHex,
        maxPriorityFeePerGasHex,
        maxFeePerGasHex,
        1L);
  }

  private static LocalDateTime expiresAt() {
    return LocalDateTime.parse("2026-05-20T10:05:00");
  }
}
