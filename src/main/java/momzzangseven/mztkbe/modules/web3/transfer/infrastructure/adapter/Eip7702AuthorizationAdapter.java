package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import org.springframework.stereotype.Component;

@Component
public class Eip7702AuthorizationAdapter implements Eip7702AuthorizationPort {

  @Override
  public String buildSigningHashHex(long chainId, String delegateTarget, BigInteger nonce) {
    return Eip7702AuthorizationHelper.buildSigningHashHex(chainId, delegateTarget, nonce);
  }

  @Override
  public boolean verifySigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress) {
    return Eip7702AuthorizationHelper.verifySigner(
        chainId, delegateTarget, nonce, signatureHex, expectedAddress);
  }

  @Override
  public Eip7702ChainPort.AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex) {
    return Eip7702AuthorizationHelper.toAuthorizationTuple(
        chainId, delegateTarget, nonce, signatureHex);
  }
}
