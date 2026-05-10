package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.Optional;

public interface GetVisibleAnswerSummaryForUpdateUseCase {

  Optional<GetAnswerSummaryUseCase.AnswerSummary> getVisibleAnswerSummaryForUpdate(Long answerId);
}
