package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QTagEntity.tagEntity;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.ManageClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.ClassTagEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.QClassTagEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.ClassTagJpaRepository;
import momzzangseven.mztkbe.modules.tag.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.out.SaveTagPort;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.springframework.stereotype.Component;
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
  @Transactional
  public void linkTagsToClass(Long classId, List<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty()) {
      return;
    }
    persistTagLinks(classId, tagNames);
  }

  @Override
  @Transactional
  public void updateTags(Long classId, List<String> tagNames) {
    classTagJpaRepository.deleteByClassId(classId);
    if (tagNames != null && !tagNames.isEmpty()) {
      // Call persistTagLinks directly (not via linkTagsToClass) to avoid Spring proxy self-invocation.
      // Self-invocation bypasses the proxy and would lose the outer transaction.
      persistTagLinks(classId, tagNames);
    }
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
        .collect(
            Collectors.groupingBy(
                tuple -> tuple.get(classTagEntity.classId),
                Collectors.mapping(tuple -> tuple.get(tagEntity.name), Collectors.toList())));
  }

  // ============================================
  // Private helpers
  // ============================================

  /**
   * Core tag-link logic shared by {@link #linkTagsToClass} and {@link #updateTags}.
   *
   * <p>Extracted to a private method so that both callers can invoke it without going through the
   * Spring proxy (which would cause self-invocation to bypass the active transaction).
   *
   * <ol>
   *   <li>Normalise tag names (trim, lowercase, deduplicate, filter blank)
   *   <li>Load already-persisted tags
   *   <li>Create missing tags via {@link SaveTagPort}
   *   <li>Insert rows into {@code class_tags}
   * </ol>
   *
   * @param classId target class ID
   * @param tagNames raw tag names from the caller
   */
  private void persistTagLinks(Long classId, List<String> tagNames) {
    // 1. 정규화 (소문자, 공백 제거, 중복 제거)
    List<String> distinctNames =
        tagNames.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(name -> !name.isBlank())
            .distinct()
            .toList();

    if (distinctNames.isEmpty()) {
      return;
    }

    // 2. 이미 존재하는 태그 조회
    List<Tag> existing = loadTagPort.loadTagsByNames(distinctNames);
    List<String> existingNames = existing.stream().map(Tag::getName).toList();

    // 3. 새 태그 생성
    List<Tag> newTags =
        distinctNames.stream()
            .filter(name -> !existingNames.contains(name))
            .map(Tag::create)
            .toList();

    List<Tag> savedNew = newTags.isEmpty() ? List.of() : saveTagPort.saveTags(newTags);

    // 4. class_tags 삽입
    List<Long> allTagIds = new ArrayList<>();
    allTagIds.addAll(existing.stream().map(Tag::getId).toList());
    allTagIds.addAll(savedNew.stream().map(Tag::getId).toList());

    List<ClassTagEntity> mappings =
        allTagIds.stream().map(tagId -> new ClassTagEntity(classId, tagId)).toList();
    classTagJpaRepository.saveAll(mappings);
  }
}
