package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateFreePostRequest(
    @NotBlank(message = "제목을 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") String content,
    List<String> imageUrls) {}
