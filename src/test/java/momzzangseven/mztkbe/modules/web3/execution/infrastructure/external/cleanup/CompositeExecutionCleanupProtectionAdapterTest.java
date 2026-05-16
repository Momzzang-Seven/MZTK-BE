package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentCleanupView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCleanupViewUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.FilterMarketplaceExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeExecutionCleanupProtectionAdapter")
class CompositeExecutionCleanupProtectionAdapterTest {

  @Mock private GetExecutionIntentCleanupViewUseCase cleanupViewUseCase;
  @Mock private FilterQnaExecutionCleanupCandidatesUseCase qnaProtection;
  @Mock private FilterMarketplaceExecutionCleanupCandidatesUseCase marketplaceProtection;
  @Mock private ObjectProvider<FilterQnaExecutionCleanupCandidatesUseCase> qnaProvider;

  @Mock
  private ObjectProvider<FilterMarketplaceExecutionCleanupCandidatesUseCase> marketplaceProvider;

  @Test
  @DisplayName("QnA와 marketplace 후보를 각각 보호 필터로 라우팅하고 나머지 intent는 삭제 가능하게 둔다")
  void filterDeletableFinalizedIntentIds_routesFeatureSpecificCandidates() {
    List<Long> candidateIds = List.of(1L, 2L, 3L);
    given(cleanupViewUseCase.getCleanupViewsByIds(candidateIds))
        .willReturn(
            List.of(
                view(
                    1L,
                    ExecutionResourceType.QUESTION,
                    "101",
                    ExecutionActionType.QNA_QUESTION_CREATE),
                view(
                    2L,
                    ExecutionResourceType.ORDER,
                    "202",
                    ExecutionActionType.MARKETPLACE_CLASS_PURCHASE),
                view(
                    3L, ExecutionResourceType.TRANSFER, "303", ExecutionActionType.TRANSFER_SEND)));
    given(qnaProvider.getIfAvailable()).willReturn(qnaProtection);
    given(marketplaceProvider.getIfAvailable()).willReturn(marketplaceProtection);
    given(qnaProtection.filterDeletableFinalizedIntentIds(List.of(1L))).willReturn(List.of(1L));
    given(marketplaceProtection.filterDeletableFinalizedIntentIds(List.of(2L)))
        .willReturn(List.of());
    CompositeExecutionCleanupProtectionAdapter adapter =
        new CompositeExecutionCleanupProtectionAdapter(
            cleanupViewUseCase, qnaProvider, marketplaceProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(1L, 3L);
  }

  private ExecutionIntentCleanupView view(
      Long id,
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionActionType actionType) {
    return new ExecutionIntentCleanupView(
        id, "intent-" + id, resourceType, resourceId, actionType, 7L);
  }
}
