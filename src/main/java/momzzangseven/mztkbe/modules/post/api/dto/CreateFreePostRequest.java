package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record CreateFreePostRequest(
    @NotBlank(message = "제목을 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") String content,
    List<@URL(message = "올바른 이미지 URL 형식이 아닙니다.") String> imageUrls,
    List<String> tags) {
  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }
}
