package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import org.springframework.data.domain.Page;

/** Output port for loading admin board post rows. */
public interface LoadManagedBoardPostsPort {

  List<ManagedBoardPostView> load(GetManagedBoardPostsQuery query);

  Page<ManagedBoardPostView> loadPage(GetManagedBoardPostsPageQuery query);
}
