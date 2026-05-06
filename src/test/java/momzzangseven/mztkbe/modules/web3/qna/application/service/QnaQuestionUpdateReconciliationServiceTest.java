package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionUpdateReconciliationService unit test")
class QnaQuestionUpdateReconciliationServiceTest {

  @Mock private QnaQuestionUpdateStatePersistencePort statePersistencePort;
  @Mock private QnaQuestionUpdateConfirmationSyncPort confirmationSyncPort;

  @InjectMocks private QnaQuestionUpdateReconciliationService service;

  @Test
  @DisplayName("reconciliation syncs confirmed candidates and records per-row failures")
  void runSyncsConfirmedCandidatesAndRecordsFailures() {
    QnaQuestionUpdateState repaired = state(101L, 1L, "intent-1");
    QnaQuestionUpdateState skipped = state(102L, 1L, "intent-2");
    QnaQuestionUpdateState failed = state(103L, 1L, "intent-3");
    when(statePersistencePort.findConfirmedIntentBoundForReconciliation(3))
        .thenReturn(List.of(repaired, skipped, failed));
    when(confirmationSyncPort.syncConfirmedQuestionUpdate("intent-1")).thenReturn(true);
    when(confirmationSyncPort.syncConfirmedQuestionUpdate("intent-2")).thenReturn(false);
    when(confirmationSyncPort.syncConfirmedQuestionUpdate("intent-3"))
        .thenThrow(new IllegalStateException("projection unavailable"));

    var result = service.run(new RunQnaQuestionUpdateReconciliationCommand(3));

    assertThat(result.scanned()).isEqualTo(3);
    assertThat(result.repaired()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    verify(statePersistencePort)
        .recordSyncFailure("intent-3", "IllegalStateException", "projection unavailable");
  }

  private QnaQuestionUpdateState state(Long postId, Long version, String intentId) {
    return QnaQuestionUpdateState.builder()
        .postId(postId)
        .requesterUserId(7L)
        .updateVersion(version)
        .updateToken("token-" + version)
        .expectedQuestionHash("0x" + "a".repeat(64))
        .executionIntentPublicId(intentId)
        .status(QnaQuestionUpdateStateStatus.INTENT_BOUND)
        .createdAt(LocalDateTime.of(2026, 4, 12, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 12, 10, 1))
        .build();
  }
}
