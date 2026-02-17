package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.tag.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.out.SaveTagPort;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.PostTagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.TagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.PostTagJpaRepository;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.TagJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagPersistenceAdapter implements LoadTagPort, SaveTagPort {

  private final TagJpaRepository tagJpaRepository;
  private final PostTagJpaRepository postTagJpaRepository;
  private final PostTagJpaRepository postTagRepository;

  @Override
  public List<Tag> loadTagsByNames(List<String> names) {
    return tagJpaRepository.findByNameIn(names).stream().map(TagEntity::toDomain).toList();
  }

  @Override
  public List<Tag> saveTags(List<Tag> tags) {
    List<TagEntity> entities = tags.stream().map(TagEntity::from).toList();

    List<TagEntity> savedEntities = tagJpaRepository.saveAll(entities);

    return savedEntities.stream().map(TagEntity::toDomain).toList();
  }

  @Override
  public void savePostTagMappings(Long postId, List<Long> tagIds) {
    List<PostTagEntity> mappingEntities =
        tagIds.stream().map(tagId -> new PostTagEntity(postId, tagId)).toList();

    postTagJpaRepository.saveAll(mappingEntities);
  }

  @Override
  public void deleteTagsByPostId(Long postId) {
    postTagRepository.deleteByPostId(postId);
  }
}
