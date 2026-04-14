package momzzangseven.mztkbe.modules.image.domain.vo;

/**
 * Represents the processing status reported by AWS Lambda in the webhook callback. This status is
 * used only when response back to lambda
 */
public enum LambdaCallbackStatus {
  COMPLETED,
  FAILED
}
