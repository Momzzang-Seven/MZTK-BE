package momzzangseven.mztkbe.modules.tag.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.tag.application.port.in.ManageTagsUseCase;
import momzzangseven.mztkbe.modules.tag.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.out.SaveTagPort;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용
public class TagService implements ManageTagsUseCase {

  private final LoadTagPort loadTagPort; // DB 조회용 포트
  private final SaveTagPort saveTagPort; // DB 저장용 포트

  /** [기능 1] 게시글 생성/수정 시 태그 연결 (Write) - 기존에 없는 태그는 새로 생성 - 게시글 ID와 태그 ID를 매핑 테이블에 저장 */
  @Override
  @Transactional // 쓰기 작업이 있으므로 트랜잭션 필요
  public void linkTagsToPost(Long postId, List<String> tagNames) {
    List<String> distinctNames = normalizeTagNames(tagNames);
    if (distinctNames.isEmpty()) {
      return;
    }

    saveTagPort.savePostTagMappings(postId, resolveTagIds(distinctNames));
  }

  @Override
  @Transactional
  public void updateTags(Long postId, List<String> tagNames) {
    List<Long> requestedTagIds = resolveTagIds(normalizeTagNames(tagNames));
    Set<Long> requested = new LinkedHashSet<>(requestedTagIds);
    Set<Long> existing = new LinkedHashSet<>(loadTagPort.loadTagIdsByPostId(postId));

    List<Long> toDelete = existing.stream().filter(tagId -> !requested.contains(tagId)).toList();
    List<Long> toInsert = requested.stream().filter(tagId -> !existing.contains(tagId)).toList();

    if (!toDelete.isEmpty()) {
      saveTagPort.deletePostTagMappings(postId, toDelete);
    }
    if (!toInsert.isEmpty()) {
      saveTagPort.savePostTagMappings(postId, toInsert);
    }
  }

  @Override
  @Transactional
  public void deleteTagsByPostId(Long postId) {
    saveTagPort.deleteTagsByPostId(postId);
  }

  private List<Long> resolveTagIds(List<String> distinctNames) {
    if (distinctNames.isEmpty()) {
      return List.of();
    }
    saveTagPort.saveTagNamesIfAbsent(distinctNames);
    return loadTagPort.loadTagsByNames(distinctNames).stream().map(Tag::getId).toList();
  }

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
}
