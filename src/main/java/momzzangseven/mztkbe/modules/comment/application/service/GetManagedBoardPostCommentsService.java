package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardPostCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentView;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetManagedBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadManagedBoardPostCommentsPort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board post comment reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagedBoardPostCommentsService implements GetManagedBoardPostCommentsUseCase {

  private final LoadManagedBoardPostCommentsPort loadManagedBoardPostCommentsPort;

  @Override
  public Page<ManagedBoardCommentView> execute(GetManagedBoardPostCommentsQuery query) {
    query.validate();
    return loadManagedBoardPostCommentsPort.load(query);
  }
}
