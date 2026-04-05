package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class Eip7702ExecutionTypedDataHelperTest {

  @Test
  void buildDigest_returns32Bytes_whenInputIsValid() {
    byte[] digest =
        Eip7702ExecutionTypedDataHelper.buildDigest(
            "MZTK",
            "1",
            11155111L,
            "0x" + "a".repeat(40),
            "prepare-1",
            "0x" + "b".repeat(64),
            BigInteger.valueOf(1_000_000L));

    assertThat(digest).hasSize(32);
  }

  @Test
  void buildDigest_rejectsBlankOrNullDomainName() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    null,
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domain name");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    " ",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domain name");
  }

  @Test
  void buildDigest_rejectsBlankOrNullDomainVersion() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    null,
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domain version");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    " ",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domain version");
  }

  @Test
  void buildDigest_rejectsNonPositiveChainId() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    0L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId");
  }

  @Test
  void buildDigest_rejectsInvalidVerifyingContract() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "not-address",
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("verifying contract");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    null,
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("verifying contract");
  }

  @Test
  void buildDigest_rejectsBlankPrepareId() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    " ",
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepareId is required");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    null,
                    "0x" + "b".repeat(64),
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepareId is required");
  }

  @Test
  void buildDigest_rejectsInvalidCallDataHash() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x1234",
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("callDataHash must be bytes32 hex");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    null,
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("callDataHash must be bytes32 hex");
  }

  @Test
  void buildDigest_rejectsNullOrNonPositiveDeadline() {
    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("deadline must be > 0");

    assertThatThrownBy(
            () ->
                Eip7702ExecutionTypedDataHelper.buildDigest(
                    "MZTK",
                    "1",
                    11155111L,
                    "0x" + "a".repeat(40),
                    "prepare-1",
                    "0x" + "b".repeat(64),
                    BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("deadline must be > 0");
  }
}
