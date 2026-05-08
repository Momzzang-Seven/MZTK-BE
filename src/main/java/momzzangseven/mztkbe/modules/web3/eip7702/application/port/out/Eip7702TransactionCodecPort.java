package momzzangseven.mztkbe.modules.web3.eip7702.application.port.out;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

public interface Eip7702TransactionCodecPort {

  String encodeTransferData(String toAddress, BigInteger amountWei);

  String hashCalls(List<BatchCall> calls);

  String encodeExecute(List<BatchCall> calls, byte[] executionSignature);

  SignedPayload signAndEncode(SignCommand command);

  record BatchCall(String to, BigInteger value, byte[] data) {}

  /**
   * Out-port command for the sponsor-side EIP-7702 sign-and-encode operation.
   *
   * <p>The {@code authorizationList} non-empty invariant mirrors {@link
   * momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignCommand} and the
   * domain-layer encoder ({@code Eip7702TxEncoder.Eip7702Fields}). Repeating it here is
   * intentional: this record is the codec port boundary every adapter crosses, so a missing {@code
   * authorizationList} surfaces as a domain-shaped {@code Web3InvalidInputException} instead of a
   * downstream NPE inside {@link
   * momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter.Eip7702TransactionCodecAdapter}.
   * In current production flows the upstream DTO already guards this invariant, so the check fires
   * defensively only — but the contract is spec-bound (length-0 type-4 transactions are invalid by
   * EIP-7702), not caller-shape-bound.
   */
  record SignCommand(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<Eip7702ChainPort.AuthorizationTuple> authorizationList,
      TreasurySigner sponsorSigner) {

    public SignCommand {
      if (sponsorSigner == null) {
        throw new Web3InvalidInputException("sponsorSigner is required");
      }
      if (authorizationList == null || authorizationList.isEmpty()) {
        throw new Web3InvalidInputException("authorizationList must be non-empty");
      }
      authorizationList = List.copyOf(authorizationList);
    }
  }

  record SignedPayload(String rawTx, String txHash) {}
}
