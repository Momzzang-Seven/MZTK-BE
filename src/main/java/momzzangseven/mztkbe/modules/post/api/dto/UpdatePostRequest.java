package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePostRequest(
    String title,
    String content,
    List<
            @NotNull(message = "Image ID must not be null.")
            @Positive(message = "Image ID must be positive.") Long>
        imageIds,
    List<@NotBlank(message = "Tag must not be blank.") String> tags) {}
