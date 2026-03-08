package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class Web3ContractPortRecordValidationTest {

  @Test
  void prevalidateCommand_acceptsValidPayload() {
    assertThatCode(
            () ->
                new Web3ContractPort.PrevalidateCommand(
                    "0x" + "a".repeat(40), "0x" + "b".repeat(40), BigInteger.ZERO))
        .doesNotThrowAnyException();
  }

  @Test
  void prevalidateCommand_rejectsInvalidAddressOrAmount() {
    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateCommand(
                    "not-address", "0x" + "b".repeat(40), BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class);

    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateCommand(
                    "0x" + "a".repeat(40), "not-address", BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class);

    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateCommand(
                    "0x" + "a".repeat(40), "0x" + "b".repeat(40), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be non-negative");

    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateCommand(
                    "0x" + "a".repeat(40), "0x" + "b".repeat(40), BigInteger.valueOf(-1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be non-negative");
  }

  @Test
  void prevalidateResult_acceptsSuccessAndFailurePayloads() {
    Web3ContractPort.PrevalidateResult success =
        new Web3ContractPort.PrevalidateResult(
            true,
            true,
            null,
            BigInteger.valueOf(21_000),
            BigInteger.ONE,
            BigInteger.TWO,
            Map.of("rpc", "ok"));
    assertThat(success.ok()).isTrue();

    // detail=null is normalized to empty map; and ok=false allows null gas fields.
    Web3ContractPort.PrevalidateResult failure =
        new Web3ContractPort.PrevalidateResult(
            false, false, "RPC_UNAVAILABLE", null, null, null, null);
    assertThat(failure.detail()).isEmpty();
  }

  @Test
  void prevalidateResult_rejectsMissingGasFieldsOnSuccess() {
    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateResult(
                    true, true, null, null, BigInteger.ONE, BigInteger.TWO, Map.of()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");

    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateResult(
                    true, true, null, BigInteger.TEN, null, BigInteger.TWO, Map.of()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");

    assertThatThrownBy(
            () ->
                new Web3ContractPort.PrevalidateResult(
                    true, true, null, BigInteger.TEN, BigInteger.ONE, null, Map.of()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void signedTransaction_rejectsNullOrBlankFields() {
    assertThatThrownBy(() -> new Web3ContractPort.SignedTransaction(null, "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rawTx is required");

    assertThatThrownBy(() -> new Web3ContractPort.SignedTransaction(" ", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rawTx is required");

    assertThatThrownBy(() -> new Web3ContractPort.SignedTransaction("0xdead", null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");

    assertThatThrownBy(() -> new Web3ContractPort.SignedTransaction("0xdead", " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void signedTransaction_acceptsValidFields() {
    assertThatCode(
            () -> new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "a".repeat(64)))
        .doesNotThrowAnyException();
  }

  @Test
  void broadcastCommand_rejectsNullOrBlankRawTx() {
    assertThatThrownBy(() -> new Web3ContractPort.BroadcastCommand(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rawTx is required");

    assertThatThrownBy(() -> new Web3ContractPort.BroadcastCommand(" "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rawTx is required");
  }

  @Test
  void broadcastCommand_acceptsValidRawTx() {
    assertThatCode(() -> new Web3ContractPort.BroadcastCommand("0xdeadbeef"))
        .doesNotThrowAnyException();
  }

  @Test
  void receiptResult_rejectsInvalidTxHashOrMissingRpcErrorReason() {
    assertThatThrownBy(
            () -> new Web3ContractPort.ReceiptResult(null, false, null, "rpc", false, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");

    assertThatThrownBy(
            () -> new Web3ContractPort.ReceiptResult(" ", false, null, "rpc", false, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");

    assertThatThrownBy(
            () ->
                new Web3ContractPort.ReceiptResult(
                    "0x" + "a".repeat(64), false, null, "rpc", true, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required when rpcError is true");

    assertThatThrownBy(
            () ->
                new Web3ContractPort.ReceiptResult(
                    "0x" + "a".repeat(64), false, null, "rpc", true, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required when rpcError is true");
  }

  @Test
  void receiptResult_acceptsValidCases() {
    assertThatCode(
            () ->
                new Web3ContractPort.ReceiptResult(
                    "0x" + "a".repeat(64), true, true, "rpc-a", false, null))
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                new Web3ContractPort.ReceiptResult(
                    "0x" + "a".repeat(64), false, null, "rpc-a", true, "RPC_TIMEOUT"))
        .doesNotThrowAnyException();
  }
}
