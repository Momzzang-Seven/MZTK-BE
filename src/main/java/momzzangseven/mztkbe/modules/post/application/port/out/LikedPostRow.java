package momzzangseven.mztkbe.modules.post.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public record LikedPostRow(Post post, Long likeId, LocalDateTime likedAt) {}
