package momzzangseven.mztkbe.global.error.verification;

import java.time.LocalDate;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;

public class VerificationAlreadyCompletedTodayException extends BusinessException {

  private final ErrorData data;

  public VerificationAlreadyCompletedTodayException(
      CompletedMethod completedMethod, LocalDate earnedDate) {
    super(ErrorCode.VERIFICATION_ALREADY_COMPLETED_TODAY);
    this.data = new ErrorData(completedMethod, earnedDate, 0);
  }

  public ErrorData getData() {
    return data;
  }

  public record ErrorData(CompletedMethod completedMethod, LocalDate earnedDate, int grantedXp) {}
}
