package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionExecutionResumePort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service for post detail/context queries.
 *
 * <p>The detail path supports optional authentication: anonymous callers still receive public post
 * data, while authenticated callers additionally get liked-state and, for question posts, the
 * latest resumable Web3 execution summary.
 */
@Service
@RequiredArgsConstructor
public class GetPostService implements GetPostUseCase, GetPostContextUseCase {

  private final PostPersistencePort postPersistencePort;
  private final CountCommentsPort countCommentsPort;
  private final LoadTagPort loadTagPort;
  private final LoadPostWriterPort loadPostWriterPort;
  private final LoadPostImagesPort loadPostImagesPort;
  private final PostLikePersistencePort postLikePersistencePort;
  private final LoadQuestionExecutionResumePort loadQuestionExecutionResumePort;

  @Override
  @Transactional(readOnly = true)
  public Optional<GetPostContextUseCase.PostContext> getPostContext(Long postId) {
    return postPersistencePort
        .loadPost(postId)
        .map(
            post ->
                new GetPostContextUseCase.PostContext(
                    post.getId(),
                    post.getUserId(),
                    post.getIsSolved(),
                    PostType.QUESTION.equals(post.getType()),
                    post.getContent(),
                    post.getReward(),
                    post.getStatus() != PostStatus.OPEN,
                    post.getStatus(),
                    post.getAcceptedAnswerId()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<GetPostContextUseCase.PostContext> getPostContextForUpdate(Long postId) {
    return postPersistencePort
        .loadPostForUpdate(postId)
        .map(
            post ->
                new GetPostContextUseCase.PostContext(
                    post.getId(),
                    post.getUserId(),
                    post.getIsSolved(),
                    PostType.QUESTION.equals(post.getType()),
                    post.getContent(),
                    post.getReward(),
                    post.getStatus() != PostStatus.OPEN,
                    post.getStatus(),
                    post.getAcceptedAnswerId()));
  }

  /** Loads public post detail, enriching optional viewer-specific state when requester is known. */
  @Override
  @Transactional(readOnly = true)
  public PostDetailResult getPost(Long postId, Long requesterUserId) {
    Post post = postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);

    List<String> tags = loadTagPort.findTagNamesByPostId(postId);
    post = post.withTags(tags);

    LoadPostWriterPort.WriterSummary writer =
        loadPostWriterPort.loadWriterById(post.getUserId()).orElse(null);

    String nickname = writer != null ? writer.nickname() : null;
    String profileImageUrl = writer != null ? writer.profileImageUrl() : null;

    PostImageResult imageResult = loadPostImagesPort.loadImages(post.getType(), post.getId());

    List<PostImageResult.PostImageSlot> imageSlots =
        imageResult == null ? List.of() : imageResult.slots();

    long likeCount = postLikePersistencePort.countByTarget(PostLikeTargetType.POST, postId);
    long commentCount = countCommentsPort.countCommentsByPostId(postId);
    boolean liked =
        requesterUserId != null
            && postLikePersistencePort.exists(PostLikeTargetType.POST, postId, requesterUserId);
    var web3Execution =
        PostType.QUESTION.equals(post.getType())
            ? loadQuestionExecutionResumePort.loadLatest(postId).orElse(null)
            : null;

    return PostDetailResult.fromDomain(
        post, likeCount, commentCount, liked, nickname, profileImageUrl, imageSlots, web3Execution);
  }
}
