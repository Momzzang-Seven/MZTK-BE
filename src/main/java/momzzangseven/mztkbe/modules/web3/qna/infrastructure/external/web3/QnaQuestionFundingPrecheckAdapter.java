package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class QnaQuestionFundingPrecheckAdapter implements PrecheckQuestionFundingPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  private final QnaRewardTokenProperties qnaRewardTokenProperties;
  private final QnaEscrowProperties qnaEscrowProperties;
  private final QnaContractCallSupport qnaContractCallSupport;

  @Override
  public void precheck(PrecheckQuestionCreateCommand command) {
    command.validate();

    String authorityAddress =
        getActiveWalletAddressUseCase
            .execute(command.requesterUserId())
            .map(address -> EvmAddress.of(address).value())
            .orElseThrow(() -> new WalletNotConnectedException(command.requesterUserId()));

    BigInteger amountWei =
        QnaEscrowIdCodec.toAmountWei(command.rewardMztk(), qnaRewardTokenProperties.getDecimals());

    if (!qnaContractCallSupport.isSupportedToken(
        qnaEscrowProperties.getQnaContractAddress(),
        qnaRewardTokenProperties.getTokenContractAddress())) {
      throw new Web3InvalidInputException("QnAEscrow does not support configured reward token");
    }

    BigInteger allowance =
        qnaContractCallSupport.loadAllowance(
            authorityAddress,
            qnaEscrowProperties.getQnaContractAddress(),
            qnaRewardTokenProperties.getTokenContractAddress());
    if (allowance.compareTo(amountWei) < 0) {
      throw new Web3InvalidInputException("insufficient allowance for QnAEscrow reward escrow");
    }
  }
}
