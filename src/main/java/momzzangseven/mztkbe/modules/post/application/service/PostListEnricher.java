package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostListEnricher {

  private final CountCommentsPort countCommentsPort;
  private final LoadTagPort loadTagPort;
  private final LoadPostWriterPort loadPostWriterPort;
  private final PostLikePersistencePort postLikePersistencePort;
  private final LoadPostImagesPort loadPostImagesPort;

  public List<PostListResult> enrich(List<Post> pagePosts, Long requesterUserId) {
    List<Long> postIds = pagePosts.stream().map(Post::getId).toList();
    Set<Long> likedPostIds =
        postLikePersistencePort.findLikedTargetIds(
            PostLikeTargetType.POST, postIds, requesterUserId);
    return enrich(pagePosts, postIds, likedPostIds == null ? Set.of() : likedPostIds, false);
  }

  public List<PostListResult> enrichAllLiked(List<Post> pagePosts) {
    List<Long> postIds = pagePosts.stream().map(Post::getId).toList();
    return enrich(pagePosts, postIds, Set.of(), true);
  }

  private List<PostListResult> enrich(
      List<Post> pagePosts, List<Long> postIds, Set<Long> likedPostIds, boolean allLiked) {
    Map<Long, List<String>> tagMap = loadTagPort.findTagsByPostIdsIn(postIds);
    Set<Long> userIds = pagePosts.stream().map(Post::getUserId).collect(Collectors.toSet());
    Map<Long, WriterSummary> writerMap = loadPostWriterPort.loadWritersByIds(userIds);
    Map<Long, Long> likeCounts =
        postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, postIds);
    Map<Long, Long> loadedCommentCounts = countCommentsPort.countCommentsByPostIds(postIds);
    Map<Long, Long> commentCounts = loadedCommentCounts == null ? Map.of() : loadedCommentCounts;

    Map<PostType, List<Long>> postIdsByType =
        pagePosts.stream()
            .collect(
                Collectors.groupingBy(
                    Post::getType, Collectors.mapping(Post::getId, Collectors.toList())));
    Map<Long, PostImageResult> imagesByPostId =
        loadPostImagesPort.loadImagesByPostIds(postIdsByType);

    return pagePosts.stream()
        .map(
            post -> {
              List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
              WriterSummary writer = writerMap.get(post.getUserId());
              String nickname = writer != null ? writer.nickname() : null;
              String profileImageUrl = writer != null ? writer.profileImageUrl() : null;
              long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
              long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
              boolean liked = allLiked || likedPostIds.contains(post.getId());
              PostImageResult images = imagesByPostId.get(post.getId());
              List<PostImageResult.PostImageSlot> imageSlots =
                  images == null ? List.of() : images.slots();
              return PostListResult.fromDomain(
                  post.withTags(tags),
                  likeCount,
                  commentCount,
                  liked,
                  nickname,
                  profileImageUrl,
                  imageSlots);
            })
        .toList();
  }
}
