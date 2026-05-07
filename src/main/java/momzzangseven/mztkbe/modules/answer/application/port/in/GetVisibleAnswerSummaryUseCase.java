package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.Optional;

public interface GetVisibleAnswerSummaryUseCase {

  Optional<GetAnswerSummaryUseCase.AnswerSummary> getVisibleAnswerSummary(Long answerId);
}
