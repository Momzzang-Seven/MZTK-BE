package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;

/** Input port for admin board post list reads. */
public interface GetManagedBoardPostsUseCase {

  List<ManagedBoardPostView> execute(GetManagedBoardPostsQuery query);
}
