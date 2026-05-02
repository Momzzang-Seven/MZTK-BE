package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;

/** Output port for loading admin board post rows. */
public interface LoadManagedBoardPostsPort {

  List<ManagedBoardPostView> load(GetManagedBoardPostsQuery query);
}
