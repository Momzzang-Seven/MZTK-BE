package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QPostTagEntity.postTagEntity;
import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QTagEntity.tagEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.PostTagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.TagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.PostTagJpaRepository;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.TagJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostTagLinkAdapter implements LoadTagPort, LinkTagPort {

  private final TagJpaRepository tagJpaRepository;
  private final PostTagJpaRepository postTagJpaRepository;
  private final JPAQueryFactory queryFactory;

  // 1.게시글-태그 연결 (저장)
  @Override
  public void linkTagsToPost(Long postId, List<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty()) return;

    List<TagEntity> tagEntities =
        tagNames.stream()
            .map(
                name ->
                    tagJpaRepository
                        .findByName(name)
                        .orElseGet(() -> tagJpaRepository.save(new TagEntity(name))))
            .toList();

    List<PostTagEntity> postTagEntities =
        tagEntities.stream().map(tag -> new PostTagEntity(postId, tag.getId())).toList();

    postTagJpaRepository.saveAll(postTagEntities);
  }

  // 2.태그 이름으로 게시글 ID 리스트 찾기 (검색 기능용)
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

  // 3.게시글 ID로 태그 이름 리스트 찾기 (목록 노출용)
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
}
