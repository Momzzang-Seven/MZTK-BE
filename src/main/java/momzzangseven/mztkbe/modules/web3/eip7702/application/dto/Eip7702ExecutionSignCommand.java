package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

/**
 * Application-layer command for the sponsor-side EIP-7702 sign-and-encode operation.
 *
 * <p>The {@code authorizationList} non-empty invariant mirrors what the EIP-7702 spec demands
 * (length-0 type-4 transactions are invalid and will be rejected by every RPC) and what the
 * domain-layer encoder ({@code Eip7702TxEncoder.Eip7702Fields}) already enforces. Repeating it
 * here is intentional: this DTO is the boundary every application caller crosses, so a missing
 * {@code authorizationList} surfaces as a domain-shaped {@code Web3InvalidInputException} instead
 * of a downstream NPE inside the codec adapter. In MZTK's current flows every caller (QnA
 * escrow / level-up reward) builds a length-1 list, but the invariant is spec-bound, not
 * caller-shape-bound — leave it as {@code !isEmpty()}.
 */
public record Eip7702ExecutionSignCommand(
    long chainId,
    BigInteger nonce,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas,
    BigInteger gasLimit,
    String to,
    BigInteger value,
    String data,
    List<Eip7702ExecutionAuthorizationTuple> authorizationList,
    TreasurySigner sponsorSigner) {

  public Eip7702ExecutionSignCommand {
    if (sponsorSigner == null) {
      throw new Web3InvalidInputException("sponsorSigner is required");
    }
    if (authorizationList == null || authorizationList.isEmpty()) {
      throw new Web3InvalidInputException("authorizationList must be non-empty");
    }
    authorizationList = List.copyOf(authorizationList);
  }
}
