package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702TransactionCodecPort;
import org.springframework.stereotype.Component;

@Component
public class Eip7702TransactionCodecAdapter implements Eip7702TransactionCodecPort {

  @Override
  public String encodeTransferData(String toAddress, java.math.BigInteger amountWei) {
    return Eip1559TransferSigner.encodeTransferData(toAddress, amountWei);
  }

  @Override
  public String hashCalls(List<BatchCall> calls) {
    List<Eip7702BatchCallAbi.Call> abiCalls =
        calls.stream()
            .map(call -> new Eip7702BatchCallAbi.Call(call.to(), call.value(), call.data()))
            .toList();
    return Eip7702BatchCallAbi.hashCalls(abiCalls);
  }

  @Override
  public String encodeExecute(List<BatchCall> calls, byte[] executionSignature) {
    List<Eip7702BatchCallAbi.Call> abiCalls =
        calls.stream()
            .map(call -> new Eip7702BatchCallAbi.Call(call.to(), call.value(), call.data()))
            .toList();
    return Eip7702BatchCallAbi.encodeExecute(abiCalls, executionSignature);
  }

  @Override
  public SignedPayload signAndEncode(SignCommand command) {
    Eip7702TransactionEncoder.SignedPayload payload =
        Eip7702TransactionEncoder.signAndEncode(
            command.chainId(),
            command.nonce(),
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas(),
            command.gasLimit(),
            command.to(),
            command.value(),
            command.data(),
            command.authorizationList(),
            command.sponsorPrivateKeyHex());
    return new SignedPayload(payload.rawTx(), payload.txHash());
  }
}
