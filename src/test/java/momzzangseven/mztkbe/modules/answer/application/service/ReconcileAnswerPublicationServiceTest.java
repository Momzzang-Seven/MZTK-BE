package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconcileAnswerPublicationService")
class ReconcileAnswerPublicationServiceTest {

  @Mock private AnswerPublicationReconciliationPort reconciliationPort;
  @Mock private PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;

  @InjectMocks private ReconcileAnswerPublicationService service;

  @Test
  @DisplayName("reconcile skips the batch when another worker owns the advisory lock")
  void reconcileSkipsWhenLockIsNotAcquired() {
    given(reconciliationPort.tryAcquireReconciliationLock()).willReturn(false);

    var result = service.reconcile(100);

    assertThat(result.total()).isZero();
    verify(reconciliationPort).tryAcquireReconciliationLock();
    verifyNoMoreInteractions(reconciliationPort, publishAnswerDeletedEventPort);
  }

  @Test
  @DisplayName("reconcile publishes delete events only for rows actually deleted")
  void reconcilePublishesOnlyActuallyDeletedAnswers() {
    given(reconciliationPort.tryAcquireReconciliationLock()).willReturn(true);
    given(reconciliationPort.reconcileConfirmedSubmits(100)).willReturn(1);
    given(reconciliationPort.reconcileTerminalSubmitFailures(100)).willReturn(2);
    given(reconciliationPort.reconcileConfirmedUpdates(100)).willReturn(3);
    given(reconciliationPort.reconcileTerminalUpdateFailures(100)).willReturn(4);
    given(reconciliationPort.findConfirmedDeleteAnswerIds(100)).willReturn(List.of(10L, 11L));
    given(reconciliationPort.deleteConfirmedDeleteAnswers(List.of(10L, 11L)))
        .willReturn(List.of(11L));
    given(reconciliationPort.reconcileTerminalDeleteRollbacks(100)).willReturn(5);
    given(reconciliationPort.repairQuestionAnswerCounts()).willReturn(6);

    var result = service.reconcile(100);

    assertThat(result.confirmedSubmits()).isEqualTo(1);
    assertThat(result.terminalSubmitFailures()).isEqualTo(2);
    assertThat(result.confirmedUpdates()).isEqualTo(3);
    assertThat(result.terminalUpdateFailures()).isEqualTo(4);
    assertThat(result.confirmedDeletes()).isEqualTo(1);
    assertThat(result.terminalDeleteRollbacks()).isEqualTo(5);
    verify(publishAnswerDeletedEventPort).publish(argThat(event -> event.answerId().equals(11L)));
    verify(publishAnswerDeletedEventPort, never())
        .publish(argThat(event -> event.answerId().equals(10L)));
    verify(reconciliationPort).repairQuestionAnswerCounts();
  }
}
