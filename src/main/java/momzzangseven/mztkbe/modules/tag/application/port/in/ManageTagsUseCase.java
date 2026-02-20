package momzzangseven.mztkbe.modules.tag.application.port.in;

import java.util.List;

public interface ManageTagsUseCase {
  /**
   * 게시글과 태그 간의 연관 관계(PostTag) 생명주기를 관리하는 유스케이스입니다. 게시글 작성, 수정, 삭제 시 발생하는 태그 매핑 데이터의 생성(Link),
   * 변경(Update), 삭제(Delete) 작업을 전담합니다.
   */
  void linkTagsToPost(Long postId, List<String> tagNames);

  void updateTags(Long postId, List<String> tagNames);

  void deleteTagsByPostId(Long postId);
}
