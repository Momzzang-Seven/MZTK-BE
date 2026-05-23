package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

public record WalletApprovalSignRequestBundle(
    AuthorizationSignRequest authorization,
    SubmitSignRequest submit,
    TransactionSignRequest transaction) {

  public static WalletApprovalSignRequestBundle forEip7702(
      AuthorizationSignRequest authorization, SubmitSignRequest submit) {
    return new WalletApprovalSignRequestBundle(authorization, submit, null);
  }

  public static WalletApprovalSignRequestBundle forEip1559(TransactionSignRequest transaction) {
    return new WalletApprovalSignRequestBundle(null, null, transaction);
  }

  public record AuthorizationSignRequest(
      long chainId, String delegateTarget, long authorityNonce, String payloadHashToSign) {}

  public record SubmitSignRequest(String executionDigest, long deadlineEpochSeconds) {}

  public record TransactionSignRequest(
      long chainId,
      String fromAddress,
      String toAddress,
      String valueHex,
      String data,
      long nonce,
      String gasLimitHex,
      String maxPriorityFeePerGasHex,
      String maxFeePerGasHex,
      long expectedNonce) {}
}
