package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.util.Erc20TransferCalldataEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.springframework.stereotype.Component;

/**
 * Thin adapter that bridges {@link Web3ContractPort.SignTransferCommand} into the shared {@link
 * SignEip1559TxUseCase} pipeline.
 *
 * <p>Builds the {@link Eip1559Fields} for an ERC-20 {@code transfer(address,uint256)} call (the
 * EVM-level recipient is encoded into calldata; the {@code to} field of the EIP-1559 envelope is
 * the token contract address) and delegates the digest signing through the shared cross-module
 * in-port (per ARCHITECTURE.md, infrastructure depends on application via port/in or port/out —
 * never on a concrete service class, and never on another module's infrastructure).
 */
@Component
@RequiredArgsConstructor
public class Eip1559TxSigningAdapter {

  private final SignEip1559TxUseCase signEip1559TxUseCase;

  /**
   * Build, sign, and assemble an ERC-20 transfer EIP-1559 transaction.
   *
   * @param command validated transfer command carrying the {@link TreasurySigner} capability handle
   * @return signed transaction envelope ready for broadcast
   */
  public Web3ContractPort.SignedTransaction signTransfer(
      Web3ContractPort.SignTransferCommand command) {
    TreasurySigner signer = command.treasurySigner();
    String calldata =
        Erc20TransferCalldataEncoder.encodeTransferData(command.toAddress(), command.amountWei());
    Eip1559Fields fields =
        new Eip1559Fields(
            command.chainId(),
            command.nonce(),
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas(),
            command.gasLimit(),
            command.tokenContractAddress(),
            BigInteger.ZERO,
            calldata);
    SignEip1559TxResult result =
        signEip1559TxUseCase.sign(
            new SignEip1559TxCommand(fields, signer.kmsKeyId(), signer.walletAddress()));
    SignedTx signed = result.signedTx();
    return new Web3ContractPort.SignedTransaction(signed.rawTx(), signed.txHash());
  }
}
