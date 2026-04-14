package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.Optional;

public interface GetAnswerSummaryForUpdateUseCase {

  Optional<GetAnswerSummaryUseCase.AnswerSummary> getAnswerSummaryForUpdate(Long answerId);
}
