package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareEip7702AuthorizationUseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrepareEip7702AuthorizationService implements PrepareEip7702AuthorizationUseCase {

  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;

  @Override
  public PrepareEip7702AuthorizationResult execute(PrepareEip7702AuthorizationCommand command) {
    BigInteger pendingNonce = eip7702ChainPort.loadPendingAccountNonce(command.authorityAddress());
    long authorityNonce;
    try {
      authorityNonce = pendingNonce.longValueExact();
    } catch (ArithmeticException e) {
      throw new Web3InvalidInputException("authority nonce overflow");
    }

    return new PrepareEip7702AuthorizationResult(
        authorityNonce,
        eip7702AuthorizationPort.buildSigningHashHex(
            command.chainId(), command.delegateTarget(), BigInteger.valueOf(authorityNonce)));
  }
}
