package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.web3j.crypto.Sign;

class Eip7702TransactionEncoderTest {

  private static final String TO = "0x" + "a".repeat(40);
  private static final String DATA = "0xdeadbeef";
  private static final String SPONSOR_PRIVATE_KEY = "0x" + "1".repeat(64);

  @Test
  void signAndEncode_returnsSignedPayload_whenInputsValid() {
    Eip7702TransactionEncoder.SignedPayload payload =
        Eip7702TransactionEncoder.signAndEncode(
            11155111L,
            BigInteger.ZERO,
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(120_000),
            TO,
            BigInteger.ZERO,
            DATA,
            List.of(validAuthTuple()),
            SPONSOR_PRIVATE_KEY);

    assertThat(payload.rawTx()).startsWith("0x04");
    assertThat(payload.txHash()).startsWith("0x").hasSize(66);
  }

  @Test
  void signAndEncode_throws_whenChainIdNotPositive() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    0L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId must be positive");
  }

  @Test
  void signAndEncode_throws_whenMaxFeeLowerThanPriorityFee() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be >= maxPriorityFeePerGas");
  }

  @Test
  void signAndEncode_throws_whenAuthorizationListEmpty() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationList is required");
  }

  @Test
  void signAndEncode_throws_whenNonceNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    null,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void signAndEncode_throws_whenNonceNegative() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.valueOf(-1),
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void signAndEncode_throws_whenPriorityFeeNonPositive() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.ZERO,
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");
  }

  @Test
  void signAndEncode_throws_whenPriorityFeeNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    null,
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");
  }

  @Test
  void signAndEncode_throws_whenMaxFeeNonPositive() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.ZERO,
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void signAndEncode_throws_whenMaxFeeNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    null,
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void signAndEncode_throws_whenGasLimitNonPositive() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.ZERO,
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");
  }

  @Test
  void signAndEncode_throws_whenGasLimitNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    null,
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");
  }

  @Test
  void signAndEncode_throws_whenToBlank() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    " ",
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("to is required");
  }

  @Test
  void signAndEncode_throws_whenToNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    null,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("to is required");
  }

  @Test
  void signAndEncode_throws_whenValueNegative() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.valueOf(-1),
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("value must be >= 0");
  }

  @Test
  void signAndEncode_throws_whenValueNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    null,
                    DATA,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("value must be >= 0");
  }

  @Test
  void signAndEncode_throws_whenDataBlank() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    " ",
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("data is required");
  }

  @Test
  void signAndEncode_throws_whenDataNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    null,
                    List.of(validAuthTuple()),
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("data is required");
  }

  @Test
  void signAndEncode_throws_whenAuthorizationListNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    null,
                    SPONSOR_PRIVATE_KEY))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationList is required");
  }

  @Test
  void signAndEncode_throws_whenSponsorPrivateKeyBlank() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsorPrivateKeyHex is required");
  }

  @Test
  void signAndEncode_throws_whenSponsorPrivateKeyNull() {
    assertThatThrownBy(
            () ->
                Eip7702TransactionEncoder.signAndEncode(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(validAuthTuple()),
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsorPrivateKeyHex is required");
  }

  @Test
  void signAndEncode_acceptsLowVSignatureValue_withoutSubtracting27() {
    byte[] r = new byte[32];
    byte[] s = new byte[32];
    r[31] = 1;
    s[31] = 2;

    try (MockedStatic<Sign> signMock = mockStatic(Sign.class, CALLS_REAL_METHODS)) {
      signMock
          .when(() -> Sign.signMessage(any(byte[].class), any()))
          .thenReturn(new Sign.SignatureData((byte) 1, r, s));

      Eip7702TransactionEncoder.SignedPayload payload =
          Eip7702TransactionEncoder.signAndEncode(
              11155111L,
              BigInteger.ZERO,
              BigInteger.valueOf(1_000_000_000L),
              BigInteger.valueOf(2_000_000_000L),
              BigInteger.valueOf(120_000),
              TO,
              BigInteger.ZERO,
              DATA,
              List.of(validAuthTuple()),
              SPONSOR_PRIVATE_KEY);

      assertThat(payload.rawTx()).startsWith("0x04");
      assertThat(payload.txHash()).startsWith("0x");
    }
  }

  private Eip7702ChainPort.AuthorizationTuple validAuthTuple() {
    return new Eip7702ChainPort.AuthorizationTuple(
        BigInteger.valueOf(11155111L),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        BigInteger.ZERO,
        BigInteger.ONE,
        BigInteger.TWO);
  }
}
