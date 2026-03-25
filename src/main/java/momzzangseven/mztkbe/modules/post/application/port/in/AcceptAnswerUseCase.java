package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerCommand;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;

public interface AcceptAnswerUseCase {

  AcceptAnswerResult execute(AcceptAnswerCommand command);
}
