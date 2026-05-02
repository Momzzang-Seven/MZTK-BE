package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.util.Erc20TransferCalldataEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.SignEip1559TxUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;
import org.springframework.stereotype.Component;

/**
 * Thin adapter that bridges {@link Web3ContractPort.SignTransferCommand} into the pure {@link
 * Eip1559TxEncoder} + {@link SignEip1559TxUseCase} pipeline.
 *
 * <p>Builds the {@link Eip1559Fields} for an ERC-20 {@code transfer(address,uint256)} call (the
 * EVM-level recipient is encoded into calldata; the {@code to} field of the EIP-1559 envelope is
 * the token contract address) and delegates the digest signing through the {@link
 * SignEip1559TxUseCase} input port (per ARCHITECTURE.md, infrastructure depends on application via
 * port/in or port/out — never on a concrete service class).
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
    SignedTx signed = signEip1559TxUseCase.sign(fields, signer.kmsKeyId(), signer.walletAddress());
    return new Web3ContractPort.SignedTransaction(signed.rawTx(), signed.txHash());
  }
}
