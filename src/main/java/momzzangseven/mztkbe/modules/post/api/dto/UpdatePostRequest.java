package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePostRequest(
    String title, String content, List<@NotNull @Positive Long> imageIds, List<String> tags) {}
