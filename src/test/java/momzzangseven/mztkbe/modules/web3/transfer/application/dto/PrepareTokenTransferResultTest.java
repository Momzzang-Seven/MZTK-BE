package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class PrepareTokenTransferResultTest {

  @Test
  void builder_createsRecord_whenValid() {
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

    PrepareTokenTransferResult result =
        PrepareTokenTransferResult.builder()
            .prepareId("prepare-1")
            .idempotencyKey("domain:QUESTION_REWARD:1:2")
            .txType("EIP7702")
            .authorityAddress("0x" + "a".repeat(40))
            .authorityNonce(5L)
            .delegateTarget("0x" + "b".repeat(40))
            .authExpiresAt(expiresAt)
            .payloadHashToSign("0x" + "c".repeat(64))
            .build();

    assertThat(result.prepareId()).isEqualTo("prepare-1");
    assertThat(result.authorityNonce()).isEqualTo(5L);
  }

  @Test
  void constructor_throws_whenAuthorityNonceNegative() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    -1L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorityNonce must be >= 0");
  }

  @Test
  void constructor_throws_whenPrepareIdBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    " ",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    0L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepareId is required");
  }

  @Test
  void constructor_throws_whenIdempotencyKeyBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    " ",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    0L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void constructor_throws_whenTxTypeBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    " ",
                    "0x" + "a".repeat(40),
                    0L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txType is required");
  }

  @Test
  void constructor_throws_whenAuthorityAddressBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    " ",
                    0L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorityAddress is required");
  }

  @Test
  void constructor_throws_whenDelegateTargetBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    0L,
                    " ",
                    LocalDateTime.now().plusMinutes(1),
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("delegateTarget is required");
  }

  @Test
  void constructor_throws_whenAuthExpiresAtNull() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    0L,
                    "0x" + "b".repeat(40),
                    null,
                    "0x" + "c".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authExpiresAt is required");
  }

  @Test
  void constructor_throws_whenPayloadHashBlank() {
    assertThatThrownBy(
            () ->
                new PrepareTokenTransferResult(
                    "prepare-1",
                    "domain:QUESTION_REWARD:1:2",
                    "EIP7702",
                    "0x" + "a".repeat(40),
                    0L,
                    "0x" + "b".repeat(40),
                    LocalDateTime.now().plusMinutes(1),
                    " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("payloadHashToSign is required");
  }
}
