package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.MarkQuestionPostSolvedCommand;

public interface MarkQuestionPostSolvedUseCase {

  int execute(MarkQuestionPostSolvedCommand command);
}
