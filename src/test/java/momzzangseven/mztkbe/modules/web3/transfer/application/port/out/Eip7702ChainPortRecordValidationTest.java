package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class Eip7702ChainPortRecordValidationTest {

  @Test
  void authorizationTuple_acceptsValidValues() {
    assertThatCode(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.valueOf(11_155_111),
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .doesNotThrowAnyException();
  }

  @Test
  void authorizationTuple_rejectsInvalidChainIdOrAddress() {
    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    null,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId must be > 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ZERO,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId must be > 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "not-address",
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void authorizationTuple_rejectsInvalidNonce() {
    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    null,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.valueOf(-1),
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void authorizationTuple_rejectsInvalidYParityRAndS() {
    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    null,
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("yParity must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.valueOf(-1),
                    BigInteger.TEN,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("yParity must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    null,
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("r must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.valueOf(-1),
                    BigInteger.valueOf(20)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("r must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("s must be >= 0");

    assertThatThrownBy(
            () ->
                new Eip7702ChainPort.AuthorizationTuple(
                    BigInteger.ONE,
                    "0x" + "a".repeat(40),
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TEN,
                    BigInteger.valueOf(-1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("s must be >= 0");
  }

  @Test
  void feePlan_acceptsValidValues() {
    assertThatCode(() -> new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.TWO))
        .doesNotThrowAnyException();
  }

  @Test
  void feePlan_rejectsNullOrNonPositiveValues() {
    assertThatThrownBy(() -> new Eip7702ChainPort.FeePlan(null, BigInteger.TWO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");

    assertThatThrownBy(() -> new Eip7702ChainPort.FeePlan(BigInteger.ZERO, BigInteger.TWO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");

    assertThatThrownBy(() -> new Eip7702ChainPort.FeePlan(BigInteger.ONE, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");

    assertThatThrownBy(() -> new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }
}

