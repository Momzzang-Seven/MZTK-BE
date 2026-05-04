package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import org.springframework.data.domain.Page;

/** Input port for admin board post list reads. */
public interface GetManagedBoardPostsUseCase {

  List<ManagedBoardPostView> execute(GetManagedBoardPostsQuery query);

  Page<ManagedBoardPostView> executePage(GetManagedBoardPostsPageQuery query);
}
