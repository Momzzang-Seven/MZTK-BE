package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.application.port.in.SyncQuestionAdminRefundStateUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAdminRefundStateSyncAdapter unit test")
class QnaAdminRefundStateSyncAdapterTest {

  @Mock private SyncQuestionAdminRefundStateUseCase syncQuestionAdminRefundStateUseCase;

  private QnaAdminRefundStateSyncAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QnaAdminRefundStateSyncAdapter(syncQuestionAdminRefundStateUseCase);
  }

  @Test
  @DisplayName("beginPendingRefund delegates to post sync use case")
  void beginPendingRefund_delegatesToUseCase() {
    adapter.beginPendingRefund(101L);

    verify(syncQuestionAdminRefundStateUseCase).beginPendingRefund(101L);
  }

  @Test
  @DisplayName("rollbackPendingRefund delegates to post sync use case")
  void rollbackPendingRefund_delegatesToUseCase() {
    adapter.rollbackPendingRefund(101L);

    verify(syncQuestionAdminRefundStateUseCase).rollbackPendingRefund(101L);
  }
}
