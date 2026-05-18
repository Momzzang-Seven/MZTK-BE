package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionActionHandlerPort resolver unit test")
class ExecutionActionHandlerPortTest {

  @Test
  @DisplayName("findMatching fails fast when multiple handlers match the same intent")
  void findMatching_throws_whenMultipleHandlersSupportSameIntent() {
    ExecutionIntent intent = intent();
    ExecutionActionHandlerPort firstHandler = mock(ExecutionActionHandlerPort.class);
    ExecutionActionHandlerPort secondHandler = mock(ExecutionActionHandlerPort.class);
    when(firstHandler.supports(ExecutionActionType.QNA_ANSWER_ACCEPT)).thenReturn(true);
    when(secondHandler.supports(ExecutionActionType.QNA_ANSWER_ACCEPT)).thenReturn(true);
    when(firstHandler.supports(intent)).thenReturn(true);
    when(secondHandler.supports(intent)).thenReturn(true);

    assertThatThrownBy(
            () ->
                ExecutionActionHandlerPort.findMatching(
                    List.of(firstHandler, secondHandler), intent))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ambiguous execution action handlers");
  }

  private ExecutionIntent intent() {
    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionActionType.QNA_ANSWER_ACCEPT,
        7L,
        22L,
        ExecutionMode.EIP7702,
        "0x" + "a".repeat(64),
        "{}",
        "0x" + "1".repeat(40),
        1L,
        "0x" + "2".repeat(40),
        LocalDateTime.of(2026, 4, 12, 10, 5),
        "0x" + "3".repeat(64),
        "0x" + "4".repeat(64),
        null,
        null,
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 12),
        LocalDateTime.of(2026, 4, 12, 10, 0));
  }
}
