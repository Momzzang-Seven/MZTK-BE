package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadManagedBoardPostsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board post list reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagedBoardPostsService implements GetManagedBoardPostsUseCase {

  private final LoadManagedBoardPostsPort loadManagedBoardPostsPort;

  @Override
  public List<ManagedBoardPostView> execute(GetManagedBoardPostsQuery query) {
    return loadManagedBoardPostsPort.load(query);
  }
}
