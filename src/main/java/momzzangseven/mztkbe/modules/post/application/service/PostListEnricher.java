package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostListEnricher {

  private final CountCommentsPort countCommentsPort;
  private final CountAnswersPort countAnswersPort;
  private final LoadTagPort loadTagPort;
  private final LoadPostWriterPort loadPostWriterPort;
  private final PostLikePersistencePort postLikePersistencePort;
  private final LoadPostImagesPort loadPostImagesPort;

  @Autowired
  public PostListEnricher(
      CountCommentsPort countCommentsPort,
      CountAnswersPort countAnswersPort,
      LoadTagPort loadTagPort,
      LoadPostWriterPort loadPostWriterPort,
      PostLikePersistencePort postLikePersistencePort,
      LoadPostImagesPort loadPostImagesPort) {
    this.countCommentsPort = countCommentsPort;
    this.countAnswersPort = countAnswersPort;
    this.loadTagPort = loadTagPort;
    this.loadPostWriterPort = loadPostWriterPort;
    this.postLikePersistencePort = postLikePersistencePort;
    this.loadPostImagesPort = loadPostImagesPort;
  }

  PostListEnricher(
      CountCommentsPort countCommentsPort,
      LoadTagPort loadTagPort,
      LoadPostWriterPort loadPostWriterPort,
      PostLikePersistencePort postLikePersistencePort,
      LoadPostImagesPort loadPostImagesPort) {
    this(
        countCommentsPort,
        new CountAnswersPort() {
          @Override
          public long countAnswers(Long postId) {
            return 0L;
          }

          @Override
          public Map<Long, Long> countAnswersByPostIds(List<Long> postIds) {
            return Map.of();
          }
        },
        loadTagPort,
        loadPostWriterPort,
        postLikePersistencePort,
        loadPostImagesPort);
  }

  public List<PostListResult> enrich(List<Post> posts, Long requesterUserId) {
    if (posts == null || posts.isEmpty()) {
      return List.of();
    }

    List<Long> postIds = posts.stream().map(Post::getId).toList();
    Set<Long> likedPostIds =
        postLikePersistencePort.findLikedTargetIds(
            PostLikeTargetType.POST, postIds, requesterUserId);
    return enrich(posts, postIds, likedPostIds == null ? Set.of() : likedPostIds, false);
  }

  public List<PostListResult> enrichAllLiked(List<Post> posts) {
    if (posts == null || posts.isEmpty()) {
      return List.of();
    }

    List<Long> postIds = posts.stream().map(Post::getId).toList();
    return enrich(posts, postIds, Set.of(), true);
  }

  private List<PostListResult> enrich(
      List<Post> posts, List<Long> postIds, Set<Long> likedPostIds, boolean allLiked) {
    Map<Long, List<String>> tagMap = loadTagPort.findTagsByPostIdsIn(postIds);
    Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
    Map<Long, WriterSummary> writerMap = loadPostWriterPort.loadWritersByIds(userIds);
    Map<Long, Long> likeCounts =
        postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, postIds);
    Map<Long, Long> loadedCommentCounts = countCommentsPort.countCommentsByPostIds(postIds);
    Map<Long, Long> commentCounts = loadedCommentCounts == null ? Map.of() : loadedCommentCounts;
    List<Long> questionPostIds =
        posts.stream()
            .filter(post -> PostType.QUESTION.equals(post.getType()))
            .map(Post::getId)
            .toList();
    Map<Long, Long> loadedAnswerCounts = countAnswersPort.countAnswersByPostIds(questionPostIds);
    Map<Long, Long> answerCounts = loadedAnswerCounts == null ? Map.of() : loadedAnswerCounts;

    Map<PostType, List<Long>> postIdsByType =
        posts.stream()
            .collect(
                Collectors.groupingBy(
                    Post::getType, Collectors.mapping(Post::getId, Collectors.toList())));
    Map<Long, PostImageResult> imagesByPostId =
        loadPostImagesPort.loadImagesByPostIds(postIdsByType);

    return posts.stream()
        .map(
            post -> {
              List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
              WriterSummary writer = writerMap.get(post.getUserId());
              String nickname = writer != null ? writer.nickname() : null;
              String profileImageUrl = writer != null ? writer.profileImageUrl() : null;
              long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
              long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
              long answerCount = answerCounts.getOrDefault(post.getId(), 0L);
              boolean liked = allLiked || likedPostIds.contains(post.getId());
              PostImageResult images = imagesByPostId.get(post.getId());
              List<PostImageResult.PostImageSlot> imageSlots =
                  images == null ? List.of() : images.slots();
              return PostListResult.fromDomain(
                  post.withTags(tags),
                  likeCount,
                  commentCount,
                  answerCount,
                  liked,
                  nickname,
                  profileImageUrl,
                  imageSlots);
            })
        .toList();
  }
}
