package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QTagEntity.tagEntity;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassTagEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.QClassTagEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.ClassTagJpaRepository;
import momzzangseven.mztkbe.modules.tag.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.out.SaveTagPort;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing {@link ManageClassTagPort} and {@link LoadClassTagPort}.
 *
 * <p>Reuses the global {@code tag} module's {@link LoadTagPort} and {@link SaveTagPort} to look up
 * / create tag rows in the global {@code tags} table, and manages the {@code class_tags} join table
 * via {@link ClassTagJpaRepository}. This is the exact same pattern as {@code PostTagLinkAdapter}.
 *
 * <p>Tag validation constraints (max 3 tags, max 30 chars each, lowercase) are enforced at the
 * domain and command layer; this adapter is purely responsible for persistence.
 */
@Component
@RequiredArgsConstructor
public class ClassTagAdapter implements ManageClassTagPort, LoadClassTagPort {

  private final LoadTagPort loadTagPort;
  private final SaveTagPort saveTagPort;
  private final ClassTagJpaRepository classTagJpaRepository;
  private final JPAQueryFactory queryFactory;

  // ========== ManageClassTagPort ==========

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void linkTagsToClass(Long classId, List<String> tagNames) {
    List<Long> requestedTagIds = resolveTagIds(normalizeTagNames(tagNames));
    if (requestedTagIds.isEmpty()) {
      return;
    }
    Set<Long> existing = new LinkedHashSet<>(classTagJpaRepository.findTagIdsByClassId(classId));
    List<Long> toInsert =
        requestedTagIds.stream().filter(tagId -> !existing.contains(tagId)).toList();
    saveClassTagMappings(classId, toInsert);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void updateTags(Long classId, List<String> tagNames) {
    List<Long> requestedTagIds = resolveTagIds(normalizeTagNames(tagNames));
    Set<Long> requested = new LinkedHashSet<>(requestedTagIds);
    Set<Long> existing = new LinkedHashSet<>(classTagJpaRepository.findTagIdsByClassId(classId));

    List<Long> toDelete = existing.stream().filter(tagId -> !requested.contains(tagId)).toList();
    List<Long> toInsert = requested.stream().filter(tagId -> !existing.contains(tagId)).toList();

    deleteClassTagMappings(classId, toDelete);
    saveClassTagMappings(classId, toInsert);
  }

  @Transactional
  @Override
  public void deleteTagsByClassId(Long classId) {
    classTagJpaRepository.deleteByClassId(classId);
  }

  // ========== LoadClassTagPort ==========

  @Override
  public List<String> findTagNamesByClassId(Long classId) {
    QClassTagEntity classTagEntity = QClassTagEntity.classTagEntity;
    return queryFactory
        .select(tagEntity.name)
        .from(classTagEntity)
        .join(tagEntity)
        .on(classTagEntity.tagId.eq(tagEntity.id))
        .where(classTagEntity.classId.eq(classId))
        .fetch();
  }

  @Override
  public Map<Long, List<String>> findTagsByClassIdsIn(List<Long> classIds) {
    QClassTagEntity classTagEntity = QClassTagEntity.classTagEntity;
    List<Tuple> results =
        queryFactory
            .select(classTagEntity.classId, tagEntity.name)
            .from(classTagEntity)
            .join(tagEntity)
            .on(classTagEntity.tagId.eq(tagEntity.id))
            .where(classTagEntity.classId.in(classIds))
            .fetch();

    return results.stream()
        .filter(tuple -> tuple.get(classTagEntity.classId) != null)
        .collect(
            Collectors.groupingBy(
                tuple -> tuple.get(classTagEntity.classId),
                Collectors.mapping(tuple -> tuple.get(tagEntity.name), Collectors.toList())));
  }

  // ============================================
  // Private helpers
  // ============================================

  private List<String> normalizeTagNames(List<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty()) {
      return List.of();
    }
    return tagNames.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .filter(name -> !name.isBlank())
        .distinct()
        .toList();
  }

  private List<Long> resolveTagIds(List<String> distinctNames) {
    if (distinctNames.isEmpty()) {
      return List.of();
    }
    saveTagPort.saveTagNamesIfAbsent(distinctNames);
    return loadTagPort.loadTagsByNames(distinctNames).stream().map(Tag::getId).toList();
  }

  private void saveClassTagMappings(Long classId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    List<ClassTagEntity> mappings =
        tagIds.stream().map(tagId -> new ClassTagEntity(classId, tagId)).toList();
    classTagJpaRepository.saveAll(mappings);
  }

  private void deleteClassTagMappings(Long classId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    classTagJpaRepository.deleteByClassIdAndTagIdIn(classId, tagIds);
  }
}
