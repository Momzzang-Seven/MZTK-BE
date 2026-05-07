package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
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

  private static final String POSTGRES_INSERT_TAG_IF_ABSENT_SQL =
      "INSERT INTO tags (name) VALUES (:name) ON CONFLICT (name) DO NOTHING";
  private static final String H2_INSERT_TAG_IF_ABSENT_SQL =
      "MERGE INTO tags (name) KEY(name) VALUES (:name)";

  private final TagJpaRepository tagJpaRepository;
  private final PostTagJpaRepository postTagJpaRepository;
  private final EntityManager entityManager;

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
  public void saveTagNamesIfAbsent(List<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty()) {
      return;
    }
    String sql = insertTagIfAbsentSql();
    for (String tagName : tagNames) {
      entityManager.createNativeQuery(sql).setParameter("name", tagName).executeUpdate();
    }
  }

  private String insertTagIfAbsentSql() {
    String persistenceProperties =
        entityManager.getEntityManagerFactory().getProperties().values().stream()
            .map(String::valueOf)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .reduce("", (left, right) -> left + " " + right);

    if (persistenceProperties.contains("h2dialect") || persistenceProperties.contains("jdbc:h2:")) {
      return H2_INSERT_TAG_IF_ABSENT_SQL;
    }
    return POSTGRES_INSERT_TAG_IF_ABSENT_SQL;
  }

  @Override
  public List<Long> loadTagIdsByPostId(Long postId) {
    return postTagJpaRepository.findTagIdsByPostId(postId);
  }

  @Override
  public void savePostTagMappings(Long postId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    List<PostTagEntity> mappingEntities =
        tagIds.stream().map(tagId -> new PostTagEntity(postId, tagId)).toList();

    postTagJpaRepository.saveAll(mappingEntities);
  }

  @Override
  public void deletePostTagMappings(Long postId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    postTagJpaRepository.deleteByPostIdAndTagIdIn(postId, tagIds);
  }

  @Override
  public void deleteTagsByPostId(Long postId) {
    postTagJpaRepository.deleteByPostId(postId);
  }
}
