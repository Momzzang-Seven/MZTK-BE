package momzzangseven.mztkbe.modules.tag.application.service;

import java.util.ArrayList;
import java.util.List;
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
    if (tagNames == null || tagNames.isEmpty()) {
      return;
    }

    // 1. 중복 제거 및 공백 제거
    List<String> distinctNames = tagNames.stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .distinct()
            .toList();

    // 2. 이미 DB에 존재하는 태그 조회
    List<Tag> existingTags = loadTagPort.loadTagsByNames(distinctNames);
    List<String> existingNames = existingTags.stream().map(Tag::getName).toList();

    // 3. 존재하지 않는 새 태그 생성
    List<Tag> newTags =
        distinctNames.stream()
            .filter(name -> !existingNames.contains(name))
            .map(Tag::create)
            .toList();

    // 4. 새 태그 저장 (ID 생성됨)
    List<Tag> savedNewTags = new ArrayList<>();
    if (!newTags.isEmpty()) {
      savedNewTags = saveTagPort.saveTags(newTags);
    }

    // 5. 전체 태그 ID 수집 (기존 태그 ID + 새 태그 ID)
    List<Long> allTagIds = new ArrayList<>();
    allTagIds.addAll(existingTags.stream().map(Tag::getId).toList());
    allTagIds.addAll(savedNewTags.stream().map(Tag::getId).toList());

    // 6. 게시글-태그 연결 (매핑 저장)
    saveTagPort.savePostTagMappings(postId, allTagIds);
  }

  @Override
  @Transactional
  public void updateTags(Long postId, List<String> tagNames) {

    saveTagPort.deleteTagsByPostId(postId);

    // 새 태그 연결
    if (tagNames != null && !tagNames.isEmpty()) {
      this.linkTagsToPost(postId, tagNames);
    }
  }

  @Override
  @Transactional
  public void deleteTagsByPostId(Long postId) {
    saveTagPort.deleteTagsByPostId(postId);
  }
}
