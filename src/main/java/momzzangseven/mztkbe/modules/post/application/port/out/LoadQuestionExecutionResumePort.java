package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.QuestionExecutionResumeView;

/** Loads the latest question execution resume summary when Web3 QnA wiring is enabled. */
public interface LoadQuestionExecutionResumePort {

  Optional<QuestionExecutionResumeView> loadLatest(Long postId);
}
