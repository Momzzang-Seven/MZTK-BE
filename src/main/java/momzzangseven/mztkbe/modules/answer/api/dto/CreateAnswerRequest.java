package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import org.hibernate.validator.constraints.URL;

public record CreateAnswerRequest(
    @NotBlank(message = "답변 내용은 필수입니다.") String content,

    // 컬렉션 내부 요소별 URL 포맷 검증 추가
    List<@URL(message = "올바른 이미지 URL 형식이 아닙니다.") String> imageUrls) {
  /**
   * @param postId URL Path Variable로 받은 게시글 ID
   * @param userId 시큐리티에서 추출한 작성자 ID
   * @return Service 계층으로 전달할 Command 객체
   */
  public CreateAnswerCommand toCommand(Long postId, Long userId) {
    return new CreateAnswerCommand(postId, userId, this.content, this.getSafeImageUrls());
  }

  // null 방지용 내부 편의 메서드
  private List<String> getSafeImageUrls() {
    return imageUrls != null ? imageUrls : List.of();
  }
}
