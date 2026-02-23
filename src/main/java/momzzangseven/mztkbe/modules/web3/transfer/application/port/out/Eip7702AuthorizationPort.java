package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;

public interface Eip7702AuthorizationPort {

  String buildSigningHashHex(long chainId, String delegateTarget, BigInteger nonce);

  boolean verifySigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress);

  Eip7702ChainPort.AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex);
}
