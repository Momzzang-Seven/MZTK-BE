package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;

/** Dedicated input port for question-post creation with optional Web3 escrow preparation. */
public interface CreateQuestionPostUseCase {

  CreateQuestionPostResult execute(CreatePostCommand command);
}
