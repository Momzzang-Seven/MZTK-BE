package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaQuestionCleanupProtectionPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaQuestionCleanupProtectionAdapter implements CheckQnaQuestionCleanupProtectionPort {

  private final GetPostContextUseCase getPostContextUseCase;

  @Override
  public boolean hasFailedQuestionCreate(Long postId, Long requesterUserId) {
    return postId != null
        && requesterUserId != null
        && getPostContextUseCase
            .getPostContext(postId)
            .filter(GetPostContextUseCase.PostContext::questionPost)
            .filter(context -> context.writerId().equals(requesterUserId))
            .filter(context -> context.publicationStatus() == PostPublicationStatus.FAILED)
            .isPresent();
  }
}
