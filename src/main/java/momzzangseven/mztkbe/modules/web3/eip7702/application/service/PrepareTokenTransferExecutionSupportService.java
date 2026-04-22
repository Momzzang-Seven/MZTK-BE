package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareTokenTransferExecutionSupportUseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrepareTokenTransferExecutionSupportService
    implements PrepareTokenTransferExecutionSupportUseCase {

  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;

  @Override
  public PrepareTokenTransferExecutionSupportResult execute(
      PrepareTokenTransferExecutionSupportCommand command) {
    BigInteger pendingNonce = eip7702ChainPort.loadPendingAccountNonce(command.authorityAddress());
    long authorityNonce;
    try {
      authorityNonce = pendingNonce.longValueExact();
    } catch (ArithmeticException e) {
      throw new Web3InvalidInputException("authority nonce overflow");
    }

    return new PrepareTokenTransferExecutionSupportResult(
        authorityNonce,
        eip7702AuthorizationPort.buildSigningHashHex(
            command.chainId(), command.delegateTarget(), BigInteger.valueOf(authorityNonce)),
        eip7702TransactionCodecPort.encodeTransferData(command.toAddress(), command.amountWei()));
  }
}
