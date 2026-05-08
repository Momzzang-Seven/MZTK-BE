package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoadQnaExecutionIntentStatePort default guard test")
class LoadQnaExecutionIntentStatePortTest {

  @Test
  @DisplayName("conflict guard checks every active intent, not only the latest active intent")
  void hasConflictingActiveIntent_checksAllActiveIntents() {
    LoadQnaExecutionIntentStatePort port =
        new StubPort(
            List.of(
                new QnaExecutionIntentStateView(
                    "intent-newer",
                    QnaExecutionActionType.QNA_ANSWER_UPDATE,
                    QnaExecutionIntentStatus.AWAITING_SIGNATURE),
                new QnaExecutionIntentStateView(
                    "intent-older",
                    QnaExecutionActionType.QNA_ANSWER_DELETE,
                    QnaExecutionIntentStatus.SIGNED)));

    boolean result =
        port.hasConflictingActiveIntent(
            QnaExecutionResourceType.ANSWER, "201", QnaExecutionActionType.QNA_ANSWER_UPDATE);

    assertThat(result).isTrue();
  }

  private record StubPort(List<QnaExecutionIntentStateView> activeIntents)
      implements LoadQnaExecutionIntentStatePort {

    @Override
    public Optional<QnaExecutionIntentStateView> loadLatestByRootIdempotencyKey(
        String rootIdempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<QnaExecutionIntentStateView> loadByExecutionIntentId(String executionIntentId) {
      return Optional.empty();
    }

    @Override
    public Optional<QnaExecutionIntentStateView> loadLatestActiveByResource(
        QnaExecutionResourceType resourceType, String resourceId) {
      return activeIntents.stream().findFirst();
    }

    @Override
    public List<QnaExecutionIntentStateView> loadActiveByResource(
        QnaExecutionResourceType resourceType, String resourceId) {
      return activeIntents;
    }

    @Override
    public Optional<QnaExecutionIntentStateView> loadLatestActiveByResourceForUpdate(
        QnaExecutionResourceType resourceType, String resourceId) {
      return activeIntents.stream().findFirst();
    }

    @Override
    public List<QnaExecutionIntentStateView> loadActiveByResourceForUpdate(
        QnaExecutionResourceType resourceType, String resourceId) {
      return activeIntents;
    }
  }
}
