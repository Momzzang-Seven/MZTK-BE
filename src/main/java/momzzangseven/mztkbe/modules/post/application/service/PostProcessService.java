package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles post update/delete mutations for both free and question boards.
 *
 * <p>Only question mutations that change the on-chain question content prepare new Web3 work.
 * Free-board mutations and metadata-only question edits intentionally return {@code web3 = null}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessService implements UpdatePostUseCase, DeletePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;
  private final LinkTagPort linkTagPort;
  private final ValidatePostImagesPort validatePostImagesPort;
  private final UpdatePostImagesPort updatePostImagesPort;
  private final CountAnswersPort countAnswersPort;
  private final QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  /**
   * Updates a post and prepares question escrow work only when question content actually changed.
   */
  @Override
  public PostMutationResult updatePost(Long currentUserId, Long postId, UpdatePostCommand command) {
    command.validate();

    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    long activeAnswerCount = countActiveAnswers(post);
    validatePostImagesIfPresent(currentUserId, postId, post.getType(), command.imageIds());

    boolean contentChanged =
        command.content() != null && !command.content().equals(post.getContent());

    Post updatedPost =
        post.update(command.title(), command.content(), command.tags(), activeAnswerCount);
    PostMutationResult preparedResult = null;
    if (PostType.QUESTION.equals(post.getType()) && command.content() != null) {
      var web3 =
          contentChanged
              ? questionLifecycleExecutionPort.prepareQuestionUpdate(
                  postId, currentUserId, updatedPost.getContent(), updatedPost.getReward())
              : questionLifecycleExecutionPort.recoverQuestionUpdate(
                  postId, currentUserId, updatedPost.getContent(), updatedPost.getReward());
      preparedResult = new PostMutationResult(postId, web3.orElse(null));
    }
    if (PostType.QUESTION.equals(post.getType())
        && preparedResult == null
        && questionLifecycleExecutionPort.hasActiveQuestionIntent(postId)) {
      throw new PostInvalidInputException(
          "Question has pending onchain mutation; wait for completion or recover first.");
    }
    postPersistencePort.savePost(updatedPost);

    if (command.tags() != null) {
      linkTagPort.updateTags(postId, command.tags());
    }

    // Delegate image sync to the output port; run only when imageIds is explicitly provided.
    if (command.imageIds() != null) {
      updatePostImagesPort.updateImages(currentUserId, postId, post.getType(), command.imageIds());
    }

    return preparedResult == null ? new PostMutationResult(postId, null) : preparedResult;
  }

  /**
   * Deletes a post and, for question posts, defers local removal until on-chain delete confirms.
   */
  @Override
  public PostMutationResult deletePost(Long currentUserId, Long postId) {
    Post post = loadPostOrThrow(postId);
    post.validateOwnership(currentUserId);
    post.validateDeletable(countActiveAnswers(post));

    if (PostType.QUESTION.equals(post.getType())) {
      var web3 =
          questionLifecycleExecutionPort.prepareQuestionDelete(
              postId, currentUserId, post.getContent(), post.getReward());
      if (web3.isPresent()) {
        return new PostMutationResult(postId, web3.get());
      }
    }
    postPersistencePort.deletePost(post);
    eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
    return new PostMutationResult(postId, null);
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

  private void validatePostImagesIfPresent(
      Long userId, Long postId, PostType postType, List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return;
    }
    validatePostImagesPort.validateAttachableImages(userId, postId, postType, imageIds);
  }
}
