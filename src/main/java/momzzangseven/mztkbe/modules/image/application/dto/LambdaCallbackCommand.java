package momzzangseven.mztkbe.modules.image.application.dto;

import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;

/**
 * Command object for the Lambda callback use case. Carries the parsed and pre-validated payload
 * from the controller.
 */
public record LambdaCallbackCommand(
    LambdaCallbackStatus status,
    String tmpObjectKey,
    String finalObjectKey, // null when status == FAILED
    String errorReason // null when status == COMPLETED
    ) {
  public void validate() {
    if (status == null) throw new IllegalArgumentException("status must not be null");
    if (tmpObjectKey == null || tmpObjectKey.isBlank())
      throw new IllegalArgumentException("tmpObjectKey must not be blank");
    if (status == LambdaCallbackStatus.COMPLETED
        && (finalObjectKey == null || finalObjectKey.isBlank()))
      throw new IllegalArgumentException("finalObjectKey is required when status is COMPLETED");
    if (status == LambdaCallbackStatus.FAILED && (finalObjectKey != null)) {
      throw new IllegalArgumentException("finalObjectKey must be null when status is FAILED");
    }
  }
}
