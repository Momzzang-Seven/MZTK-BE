package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QPostTagEntity.postTagEntity;
import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QTagEntity.tagEntity;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.in.TagLinkUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostTagLinkAdapter implements LoadTagPort, LinkTagPort {

  private final TagLinkUseCase tagLinkUseCase;
  private final JPAQueryFactory queryFactory;

  // 1. 게시글-태그 연결 (저장)
  @Override
  public void linkTagsToPost(Long postId, List<String> tagNames) {
    tagLinkUseCase.linkTagsToPost(postId, tagNames);
  }

  // 2. 태그 이름으로 게시글 ID 찾기 (검색용)
  @Override
  public List<Long> findPostIdsByTagName(String tagName) {
    return queryFactory
        .select(postTagEntity.postId)
        .from(tagEntity)
        .join(postTagEntity)
        .on(tagEntity.id.eq(postTagEntity.tagId))
        .where(tagEntity.name.eq(tagName))
        .fetch();
  }

  // 3. 단건 조회 메서드
  @Override
  public List<String> findTagNamesByPostId(Long postId) {
    return queryFactory
        .select(tagEntity.name)
        .from(postTagEntity)
        .join(tagEntity)
        .on(postTagEntity.tagId.eq(tagEntity.id))
        .where(postTagEntity.postId.eq(postId))
        .fetch();
  }

  // 4. 다건 조회 메서드 (Hibernate 6 호환성 해결)
  @Override
  public Map<Long, List<String>> findTagsByPostIdsIn(List<Long> postIds) {
    List<Tuple> results =
        queryFactory
            .select(postTagEntity.postId, tagEntity.name)
            .from(postTagEntity)
            .join(tagEntity)
            .on(postTagEntity.tagId.eq(tagEntity.id))
            .where(postTagEntity.postId.in(postIds))
            .fetch();

    // 2. Java Stream을 사용하여 메모리에서 Grouping 합니다.
    return results.stream()
        .collect(
            Collectors.groupingBy(
                tuple -> tuple.get(postTagEntity.postId),
                Collectors.mapping(tuple -> tuple.get(tagEntity.name), Collectors.toList())));
  }

  @Override
  public void updateTags(Long postId, List<String> tagNames) {
    tagLinkUseCase.updateTags(postId, tagNames);
  }
}
