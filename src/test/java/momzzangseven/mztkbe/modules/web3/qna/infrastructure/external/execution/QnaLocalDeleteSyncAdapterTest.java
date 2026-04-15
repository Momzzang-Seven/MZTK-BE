package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionDeleteSyncUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaLocalDeleteSyncAdapter unit test")
class QnaLocalDeleteSyncAdapterTest {

  @Mock private ConfirmQuestionDeleteSyncUseCase confirmQuestionDeleteSyncUseCase;
  @Mock private ConfirmAnswerDeleteSyncUseCase confirmAnswerDeleteSyncUseCase;

  @InjectMocks private QnaLocalDeleteSyncAdapter qnaLocalDeleteSyncAdapter;

  @Test
  @DisplayName("question delete confirmation delegates to the post module input port")
  void confirmQuestionDeleted_delegatesToPostUseCase() {
    qnaLocalDeleteSyncAdapter.confirmQuestionDeleted(101L);

    verify(confirmQuestionDeleteSyncUseCase).confirmDeleted(101L);
  }

  @Test
  @DisplayName("answer delete confirmation delegates to the answer module input port")
  void confirmAnswerDeleted_delegatesToAnswerUseCase() {
    qnaLocalDeleteSyncAdapter.confirmAnswerDeleted(201L);

    verify(confirmAnswerDeleteSyncUseCase).confirmDeleted(201L);
  }
}
