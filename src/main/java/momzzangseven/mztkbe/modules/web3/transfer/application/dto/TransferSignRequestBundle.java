package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

public record TransferSignRequestBundle(
    AuthorizationSignRequest authorization,
    SubmitSignRequest submit,
    TransactionSignRequest transaction) {

  public static TransferSignRequestBundle forEip7702(
      AuthorizationSignRequest authorization, SubmitSignRequest submit) {
    return new TransferSignRequestBundle(authorization, submit, null);
  }

  public static TransferSignRequestBundle forEip1559(TransactionSignRequest transaction) {
    return new TransferSignRequestBundle(null, null, transaction);
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
