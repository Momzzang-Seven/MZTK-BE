package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerOrphanCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerOrphanCleanupService 단위 테스트")
class AnswerOrphanCleanupServiceTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteAnswerPort deleteAnswerPort;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AnswerOrphanCleanupProperties props;

  @InjectMocks private AnswerOrphanCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    given(props.getBatchSize()).willReturn(100);
  }

  @Test
  @DisplayName("고아 답변이 없으면 0을 반환하고 아무 작업도 하지 않는다")
  void runBatch_noOrphans_returnsZero() {
    given(loadAnswerPort.loadOrphanAnswerIds(100)).willReturn(List.of());

    int result = cleanupService.runBatch();

    assertThat(result).isZero();
    verify(deleteAnswerPort, never()).deleteAnswersByIds(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("고아 답변을 일괄 삭제하고 각 answerId에 대해 AnswerDeletedEvent를 발행한다")
  void runBatch_deletesOrphansAndPublishesEvents() {
    given(loadAnswerPort.loadOrphanAnswerIds(100)).willReturn(List.of(10L, 11L));

    int result = cleanupService.runBatch();

    assertThat(result).isEqualTo(2);
    verify(deleteAnswerPort).deleteAnswersByIds(List.of(10L, 11L));
    verify(eventPublisher).publishEvent(new AnswerDeletedEvent(10L));
    verify(eventPublisher).publishEvent(new AnswerDeletedEvent(11L));
  }

  @Test
  @DisplayName("batch size가 0 이하이면 예외를 던지고 cleanup을 수행하지 않는다")
  void runBatch_invalidBatchSize_throwsIllegalStateException() {
    given(props.getBatchSize()).willReturn(0);

    assertThatThrownBy(() -> cleanupService.runBatch())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("answer.orphan-cleanup.batch-size must be positive");

    verify(loadAnswerPort, never()).loadOrphanAnswerIds(any(Integer.class));
    verify(deleteAnswerPort, never()).deleteAnswersByIds(any());
    verify(eventPublisher, never()).publishEvent(any());
  }
}
