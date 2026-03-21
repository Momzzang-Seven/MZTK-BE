package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;

public record UpdatePostRequest(
    String title, String content, List<Long> imageIds, List<String> tags) {}
