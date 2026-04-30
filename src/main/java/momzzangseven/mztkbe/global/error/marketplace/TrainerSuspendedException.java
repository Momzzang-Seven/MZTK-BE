package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a suspended trainer attempts a restricted operation. */
public class TrainerSuspendedException extends BusinessException {

  public TrainerSuspendedException(Long trainerId) {
    super(
        ErrorCode.MARKETPLACE_TRAINER_SUSPENDED,
        "Trainer is suspended and cannot perform this action: trainerId=" + trainerId);
  }

  /** Used when trainer ID context is not available at the throw site. */
  public TrainerSuspendedException() {
    super(ErrorCode.MARKETPLACE_TRAINER_SUSPENDED);
  }
}
