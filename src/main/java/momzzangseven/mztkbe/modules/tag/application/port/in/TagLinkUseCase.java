package momzzangseven.mztkbe.modules.tag.application.port.in;

import java.util.List;

public interface TagLinkUseCase {
  /**
   * 게시글에 태그를 연결(저장)합니다.
   *
   * @param postId 게시글 ID
   * @param tagNames 태그 이름 리스트 (예: ["오운완", "3대500"])
   */
  void linkTagsToPost(Long postId, List<String> tagNames);
}
