package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.time.LocalDateTime;

public interface CleanupAnswerPreparationUseCase {

  CleanupAnswerPreparationResult cleanupExpiredPreparations(LocalDateTime now, int batchSize);

  record CleanupAnswerPreparationResult(
      int createReservationsDeleted, int deletePreparationsExpired, int updatePreparationsExpired) {

    public int total() {
      return createReservationsDeleted + deletePreparationsExpired + updatePreparationsExpired;
    }
  }
}
