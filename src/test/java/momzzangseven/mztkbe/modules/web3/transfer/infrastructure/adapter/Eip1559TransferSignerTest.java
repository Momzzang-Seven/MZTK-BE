package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.junit.jupiter.api.Test;

class Eip1559TransferSignerTest {

  @Test
  void encodeTransferData_encodesTransferFunction() {
    String data =
        Eip1559TransferSigner.encodeTransferData("0x" + "b".repeat(40), BigInteger.valueOf(123));

    assertThat(data).startsWith("0xa9059cbb");
  }

  @Test
  void encodeTransferData_throws_whenAmountNegative() {
    assertThatThrownBy(
            () ->
                Eip1559TransferSigner.encodeTransferData(
                    "0x" + "b".repeat(40), BigInteger.valueOf(-1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be >= 0");
  }

  @Test
  void signTransfer_returnsSignedTransaction_whenCommandValid() {
    Web3ContractPort.SignedTransaction signed = Eip1559TransferSigner.signTransfer(validCommand());

    assertThat(signed.rawTx()).startsWith("0x");
    assertThat(signed.txHash()).startsWith("0x").hasSize(66);
  }

  @Test
  void signTransfer_throws_whenCommandNull() {
    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void signTransfer_throws_whenPrivateKeyBlank() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            " ",
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            11155111L,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryPrivateKeyHex is required");
  }

  @Test
  void signTransfer_throws_whenNonceNegative() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            -1L,
            11155111L,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void signTransfer_throws_whenChainIdNotPositive() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            0L,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId must be > 0");
  }

  @Test
  void signTransfer_throws_whenGasLimitNotPositive() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            11155111L,
            BigInteger.ZERO,
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");
  }

  @Test
  void signTransfer_throws_whenMaxPriorityFeeNotPositive() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            11155111L,
            BigInteger.valueOf(21000),
            BigInteger.ZERO,
            BigInteger.valueOf(2_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");
  }

  @Test
  void signTransfer_throws_whenMaxFeeNotPositive() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            11155111L,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.ZERO);

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void signTransfer_throws_whenMaxFeeLowerThanPriority() {
    Web3ContractPort.SignTransferCommand command =
        new Web3ContractPort.SignTransferCommand(
            "0x" + "1".repeat(64),
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            0L,
            11155111L,
            BigInteger.valueOf(21000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(1_000_000_000L));

    assertThatThrownBy(() -> Eip1559TransferSigner.signTransfer(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be >= maxPriorityFeePerGas");
  }

  private Web3ContractPort.SignTransferCommand validCommand() {
    return new Web3ContractPort.SignTransferCommand(
        "0x" + "1".repeat(64),
        "0x" + "a".repeat(40),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        1L,
        11155111L,
        BigInteger.valueOf(60_000),
        BigInteger.valueOf(1_000_000_000L),
        BigInteger.valueOf(2_000_000_000L));
  }
}

