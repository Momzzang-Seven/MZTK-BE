package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Eip7702ExecutionSignCommand compact constructor invariants")
class Eip7702ExecutionSignCommandTest {

  private static final long CHAIN_ID = 11155111L;
  private static final BigInteger NONCE = BigInteger.ZERO;
  private static final BigInteger MAX_PRIORITY = BigInteger.valueOf(2_000_000_000L);
  private static final BigInteger MAX_FEE = BigInteger.valueOf(40_000_000_000L);
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(210_000);
  private static final String TO = "0x" + "1".repeat(40);
  private static final BigInteger VALUE = BigInteger.ZERO;
  private static final String DATA = "0x";
  private static final TreasurySigner SIGNER =
      new TreasurySigner("alias", "alias/key", "0x" + "c".repeat(40));

  private static Eip7702ExecutionAuthorizationTuple tuple() {
    return new Eip7702ExecutionAuthorizationTuple(
        BigInteger.valueOf(CHAIN_ID),
        "0x" + "b".repeat(40),
        BigInteger.ZERO,
        BigInteger.ZERO,
        BigInteger.ONE,
        BigInteger.ONE);
  }

  @Test
  @DisplayName("authorizationList = null → Web3InvalidInputException (NPE 가 아닌 도메인 예외)")
  void constructor_authListNull_throws() {
    assertThatThrownBy(
            () ->
                new Eip7702ExecutionSignCommand(
                    CHAIN_ID,
                    NONCE,
                    MAX_PRIORITY,
                    MAX_FEE,
                    GAS_LIMIT,
                    TO,
                    VALUE,
                    DATA,
                    null,
                    SIGNER))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationList");
  }

  @Test
  @DisplayName("authorizationList = empty → Web3InvalidInputException (EIP-7702 spec)")
  void constructor_authListEmpty_throws() {
    assertThatThrownBy(
            () ->
                new Eip7702ExecutionSignCommand(
                    CHAIN_ID,
                    NONCE,
                    MAX_PRIORITY,
                    MAX_FEE,
                    GAS_LIMIT,
                    TO,
                    VALUE,
                    DATA,
                    List.of(),
                    SIGNER))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationList");
  }

  @Test
  @DisplayName("sponsorSigner = null → Web3InvalidInputException")
  void constructor_signerNull_throws() {
    assertThatThrownBy(
            () ->
                new Eip7702ExecutionSignCommand(
                    CHAIN_ID,
                    NONCE,
                    MAX_PRIORITY,
                    MAX_FEE,
                    GAS_LIMIT,
                    TO,
                    VALUE,
                    DATA,
                    List.of(tuple()),
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsorSigner");
  }

  @Test
  @DisplayName("happy path → command holds the supplied authorization list")
  void constructor_happyPath_succeeds() {
    Eip7702ExecutionSignCommand command =
        new Eip7702ExecutionSignCommand(
            CHAIN_ID,
            NONCE,
            MAX_PRIORITY,
            MAX_FEE,
            GAS_LIMIT,
            TO,
            VALUE,
            DATA,
            List.of(tuple()),
            SIGNER);

    assertThat(command.authorizationList()).hasSize(1);
    assertThat(command.sponsorSigner()).isEqualTo(SIGNER);
  }
}
