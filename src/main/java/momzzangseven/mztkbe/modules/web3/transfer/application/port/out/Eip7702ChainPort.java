package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import java.util.List;

/** RPC port for EIP-7702 nonce/gas queries. */
public interface Eip7702ChainPort {

  BigInteger loadPendingAccountNonce(String address);

  BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authList);

  FeePlan loadSponsorFeePlan();

  record AuthorizationTuple(
      BigInteger chainId,
      String address,
      BigInteger nonce,
      BigInteger yParity,
      BigInteger r,
      BigInteger s) {}

  record FeePlan(BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}
}
