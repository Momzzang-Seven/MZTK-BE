package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

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
      BigInteger s) {

    public AuthorizationTuple {
      validate(chainId, address, nonce, yParity, r, s);
    }

    private static void validate(
        BigInteger chainId,
        String address,
        BigInteger nonce,
        BigInteger yParity,
        BigInteger r,
        BigInteger s) {
      if (chainId == null || chainId.signum() <= 0) {
        throw new Web3InvalidInputException("chainId must be > 0");
      }
      EvmAddress.of(address);
      if (nonce == null || nonce.signum() < 0) {
        throw new Web3InvalidInputException("nonce must be >= 0");
      }
      if (yParity == null || yParity.signum() < 0) {
        throw new Web3InvalidInputException("yParity must be >= 0");
      }
      if (r == null || r.signum() < 0) {
        throw new Web3InvalidInputException("r must be >= 0");
      }
      if (s == null || s.signum() < 0) {
        throw new Web3InvalidInputException("s must be >= 0");
      }
    }
  }

  record FeePlan(BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {

    public FeePlan {
      if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
      }
      if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxFeePerGas must be > 0");
      }
    }
  }
}
