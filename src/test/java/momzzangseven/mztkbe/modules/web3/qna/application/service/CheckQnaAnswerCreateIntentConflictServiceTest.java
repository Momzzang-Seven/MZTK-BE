package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort.QnaAnswerExecutionIntentRef;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckQnaAnswerCreateIntentConflictService")
class CheckQnaAnswerCreateIntentConflictServiceTest {

  @Mock private ManageQnaAnswerExecutionIntentRefPort refPort;
  @Mock private LoadQnaExecutionIntentStatePort loadStatePort;

  @InjectMocks private CheckQnaAnswerCreateIntentConflictService service;

  @Test
  @DisplayName("returns true when a submit ref has active execution state")
  void hasActiveAnswerCreateIntent_activeSubmitExists() {
    when(refPort.findByPostIdAndActionType(10L, QnaExecutionActionType.QNA_ANSWER_SUBMIT))
        .thenReturn(
            List.of(
                new QnaAnswerExecutionIntentRef(
                    "intent-1", 10L, 20L, QnaExecutionActionType.QNA_ANSWER_SUBMIT, "SIGNED")));
    when(loadStatePort.loadByExecutionIntentId("intent-1"))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT,
                    QnaExecutionIntentStatus.SIGNED)));

    assertThat(service.hasActiveAnswerCreateIntent(10L)).isTrue();
  }

  @Test
  @DisplayName("returns false for terminal submit refs")
  void hasActiveAnswerCreateIntent_terminalSubmitOnly() {
    when(refPort.findByPostIdAndActionType(10L, QnaExecutionActionType.QNA_ANSWER_SUBMIT))
        .thenReturn(
            List.of(
                new QnaAnswerExecutionIntentRef(
                    "intent-1", 10L, 20L, QnaExecutionActionType.QNA_ANSWER_SUBMIT, "EXPIRED")));
    when(loadStatePort.loadByExecutionIntentId("intent-1"))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT,
                    QnaExecutionIntentStatus.EXPIRED)));

    assertThat(service.hasActiveAnswerCreateIntent(10L)).isFalse();
  }
}
