package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostTargetView;

/** Output port for loading one post target for admin board moderation. */
public interface LoadManagedBoardPostPort {

  Optional<ManagedBoardPostTargetView> load(Long postId);
}
