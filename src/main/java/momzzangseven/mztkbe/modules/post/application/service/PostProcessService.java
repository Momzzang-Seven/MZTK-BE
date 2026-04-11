package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;
  private final LinkTagPort linkTagPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final CountAnswersPort countAnswersPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Override
  public void updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    command.validate();

    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    long activeAnswerCount = countActiveAnswers(post);

    Post updatedPost =
        post.update(command.title(), command.content(), command.tags(), activeAnswerCount);
    postPersistencePort.savePost(updatedPost);

    if (command.tags() != null) {
      linkTagPort.updateTags(postId, command.tags());
    }

    // Delegate image sync to the output port; run only when imageIds is explicitly provided.
    if (command.imageIds() != null) {
      updatePostImagesPort.updateImages(currentUserId, postId, post.getType(), command.imageIds());
    }

    if (PostType.QUESTION.equals(post.getType()) && command.content() != null) {
      questionLifecycleExecutionPort.prepareQuestionUpdate(
          postId, currentUserId, updatedPost.getContent(), updatedPost.getReward());
    }
  }

  @Override
  public void deletePost(Long currentUserId, Long postId) {
    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    post.validateDeletable(countActiveAnswers(post));

    postPersistencePort.deletePost(post);
    if (PostType.QUESTION.equals(post.getType())) {
      questionLifecycleExecutionPort.prepareQuestionDelete(
          postId, currentUserId, post.getContent(), post.getReward());
    }
    eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
  }

  private Post loadPostOrThrow(Long postId) {
    return postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);
  }

  private long countActiveAnswers(Post post) {
    if (!PostType.QUESTION.equals(post.getType())) {
      return 0L;
    }
    return countAnswersPort.countAnswers(post.getId());
  }
}
