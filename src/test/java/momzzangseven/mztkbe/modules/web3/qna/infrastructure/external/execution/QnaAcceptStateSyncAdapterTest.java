package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.application.port.in.SyncAcceptedAnswerUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAcceptStateSyncAdapter unit test")
class QnaAcceptStateSyncAdapterTest {

  @Mock private SyncAcceptedAnswerUseCase syncAcceptedAnswerUseCase;

  private QnaAcceptStateSyncAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QnaAcceptStateSyncAdapter(syncAcceptedAnswerUseCase);
  }

  @Test
  @DisplayName("confirmAccepted delegates to post sync use case")
  void confirmAccepted_delegatesToUseCase() {
    adapter.confirmAccepted(101L, 201L);

    verify(syncAcceptedAnswerUseCase).confirmAccepted(101L, 201L);
  }

  @Test
  @DisplayName("rollbackPendingAccept delegates to post sync use case")
  void rollbackPendingAccept_delegatesToUseCase() {
    adapter.rollbackPendingAccept(101L, 201L);

    verify(syncAcceptedAnswerUseCase).rollbackPendingAccept(101L, 201L);
  }
}
