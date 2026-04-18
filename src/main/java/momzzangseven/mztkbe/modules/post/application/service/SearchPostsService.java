package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsResult;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchPostsService implements SearchPostsUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;
  private final LoadPostWriterPort loadPostWriterPort;
  private final PostLikePersistencePort postLikePersistencePort;
  private final LoadPostImagesPort loadPostImagesPort;

  @Override
  public SearchPostsResult searchPosts(PostSearchCondition condition, Long requesterUserId) {
    List<Long> filteredPostIds = null;
    if (StringUtils.hasText(condition.tagName())) {
      filteredPostIds = loadTagPort.findPostIdsByTagName(condition.tagName());
      if (filteredPostIds.isEmpty()) {
        return new SearchPostsResult(List.of(), false);
      }
    }

    // 1. size + 1 probe row를 포함해 게시글 조회
    List<Post> posts = postPersistencePort.findPostsByCondition(condition, filteredPostIds);
    boolean hasNext = posts.size() > condition.size();
    List<Post> pagePosts = hasNext ? posts.subList(0, condition.size()) : posts;
    if (pagePosts.isEmpty()) {
      return new SearchPostsResult(List.of(), false);
    }

    // 2. 게시글 ID 목록 추출
    List<Long> postIds = pagePosts.stream().map(Post::getId).toList();

    // 3. 태그 일괄 조회
    Map<Long, List<String>> tagMap = loadTagPort.findTagsByPostIdsIn(postIds);

    // 4. 작성자 일괄 조회
    Set<Long> userIds = pagePosts.stream().map(Post::getUserId).collect(Collectors.toSet());
    Map<Long, WriterSummary> writerMap = loadPostWriterPort.loadWritersByIds(userIds);
    Map<Long, Long> likeCounts =
        postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, postIds);
    Set<Long> likedPostIds =
        postLikePersistencePort.findLikedTargetIds(
            PostLikeTargetType.POST, postIds, requesterUserId);

    // 5. 이미지 일괄 조회 (PostType 별 그룹핑 후 배치 호출)
    Map<PostType, List<Long>> postIdsByType =
        pagePosts.stream()
            .collect(
                Collectors.groupingBy(
                    Post::getType, Collectors.mapping(Post::getId, Collectors.toList())));
    Map<Long, PostImageResult> imagesByPostId =
        loadPostImagesPort.loadImagesByPostIds(postIdsByType);

    // 6. 메모리에서 PostListResult 조립
    List<PostListResult> results =
        pagePosts.stream()
            .map(
                post -> {
                  List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
                  WriterSummary writer = writerMap.get(post.getUserId());
                  String nickname = writer != null ? writer.nickname() : null;
                  String profileImageUrl = writer != null ? writer.profileImageUrl() : null;
                  long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
                  boolean liked = likedPostIds.contains(post.getId());
                  PostImageResult images = imagesByPostId.get(post.getId());
                  List<String> imageUrls =
                      images == null
                          ? List.of()
                          : images.slots().stream().map(slot -> slot.imageUrl()).toList();
                  return PostListResult.fromDomain(
                      post.withTags(tags), likeCount, liked, nickname, profileImageUrl, imageUrls);
                })
            .toList();
    return new SearchPostsResult(results, hasNext);
  }
}
