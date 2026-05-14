package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;

final class WalletApprovalSponsorLimitMapper {

  private WalletApprovalSponsorLimitMapper() {}

  static RuntimeException map(Web3TransferException exception) {
    if (isSponsorLimitExceeded(exception)) {
      return new WalletApprovalUnavailableException(exception.getMessage(), exception);
    }
    return exception;
  }

  private static boolean isSponsorLimitExceeded(Web3TransferException exception) {
    return ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED.getCode().equals(exception.getCode());
  }
}
