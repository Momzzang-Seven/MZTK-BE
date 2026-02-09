package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateFreePostRequest(
    @NotNull(message = "userId는 필수입니다.") Long userId,
    @NotBlank(message = "제목을 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") String content,
    List<String> imageUrls) {}
