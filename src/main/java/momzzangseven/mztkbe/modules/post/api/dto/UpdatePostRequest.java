package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;

public record UpdatePostRequest(String title, String content, List<String> imageUrls) {}
