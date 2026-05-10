package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentSearchView;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetManagedBoardCommentsUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadManagedBoardCommentsPort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board global comment search reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagedBoardCommentsService implements GetManagedBoardCommentsUseCase {

  private final LoadManagedBoardCommentsPort loadManagedBoardCommentsPort;

  @Override
  public Page<ManagedBoardCommentSearchView> execute(GetManagedBoardCommentsQuery query) {
    return loadManagedBoardCommentsPort.load(query);
  }
}
