package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.SignEip7702TxPort;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.AuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.Eip7702Fields;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.util.Erc20TransferCalldataEncoder;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

/**
 * Thin adapter that bridges {@link Eip7702TransactionCodecPort.SignCommand} into the pure {@link
 * Eip7702TxEncoder} + {@link SignEip7702TxPort} pipeline.
 *
 * <p>Converts the infra-level {@link Eip7702ChainPort.AuthorizationTuple} (BigInteger r/s) into the
 * domain-level {@link AuthorizationTuple} (32-byte big-endian arrays) and delegates digest signing
 * to {@link SignEip7702TxPort}.
 */
@Component
@RequiredArgsConstructor
public class Eip7702TransactionCodecAdapter implements Eip7702TransactionCodecPort {

  private static final int SCALAR_BYTE_LENGTH = 32;

  private final SignEip7702TxPort signEip7702TxPort;

  @Override
  public String encodeTransferData(String toAddress, BigInteger amountWei) {
    return Erc20TransferCalldataEncoder.encodeTransferData(toAddress, amountWei);
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
    Eip7702Fields fields =
        new Eip7702Fields(
            command.chainId(),
            command.nonce(),
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas(),
            command.gasLimit(),
            command.to(),
            command.value(),
            command.data(),
            command.authorizationList().stream().map(this::toDomainAuthTuple).toList());

    SignedTx signed = signEip7702TxPort.sign(fields, command.sponsorSigner());
    return new SignedPayload(signed.rawTx(), signed.txHash());
  }

  // Convert infra BigInteger r/s into 32-byte big-endian arrays the domain encoder expects.
  private AuthorizationTuple toDomainAuthTuple(Eip7702ChainPort.AuthorizationTuple tuple) {
    long chainId = tuple.chainId().longValueExact();
    byte yParity = (byte) tuple.yParity().intValueExact();
    byte[] r = Numeric.toBytesPadded(tuple.r(), SCALAR_BYTE_LENGTH);
    byte[] s = Numeric.toBytesPadded(tuple.s(), SCALAR_BYTE_LENGTH);
    return new AuthorizationTuple(chainId, tuple.address(), tuple.nonce(), yParity, r, s);
  }
}
