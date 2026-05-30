package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import org.springframework.stereotype.Service;

/**
 * Orchestrates comment creation as two sequential transactions: T1 saves the comment (commits and
 * releases its connection), then T2 grants XP on a fresh connection.
 *
 * <p>Intentionally <b>not</b> {@code @Transactional} so T1's connection is released before the XP
 * grant runs — the request holds at most one connection at a time. The comment API does not expose
 * XP, so the grant result is not reflected in the response; failures are made durable by the
 * guaranteed-delivery path behind {@link GrantCommentXpPort}.
 */
@Service
@RequiredArgsConstructor
public class CreateCommentFacade implements CreateCommentUseCase {

  private final CommentService commentService;
  private final GrantCommentXpPort grantCommentXpPort;

  @Override
  public CommentMutationResult createComment(CreateCommentCommand command) {
    CommentMutationResult result = commentService.createComment(command);
    grantCommentXpPort.grantCreateCommentXp(result.writerId(), result.id());
    return result;
  }
}
