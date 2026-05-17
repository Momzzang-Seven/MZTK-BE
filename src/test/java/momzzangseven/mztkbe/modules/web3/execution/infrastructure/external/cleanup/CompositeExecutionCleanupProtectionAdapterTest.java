package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
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

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private FilterQnaExecutionCleanupCandidatesUseCase qnaProtection;
  @Mock private FilterMarketplaceExecutionCleanupCandidatesUseCase marketplaceProtection;
  @Mock private ObjectProvider<FilterQnaExecutionCleanupCandidatesUseCase> qnaProvider;

  @Mock
  private ObjectProvider<FilterMarketplaceExecutionCleanupCandidatesUseCase> marketplaceProvider;

  @Test
  @DisplayName("QnA와 marketplace 후보를 각각 보호 필터로 라우팅하고 나머지 intent는 삭제 가능하게 둔다")
  void filterDeletableFinalizedIntentIds_routesFeatureSpecificCandidates() {
    List<Long> candidateIds = List.of(1L, 2L, 3L);
    given(executionIntentPersistencePort.findAllByIdsForUpdate(candidateIds))
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
            executionIntentPersistencePort, qnaProvider, marketplaceProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(1L, 3L);
  }

  private ExecutionIntent view(
      Long id,
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionActionType actionType) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 17, 10, 0);
    return ExecutionIntent.builder()
        .id(id)
        .publicId("intent-" + id)
        .rootIdempotencyKey("root-" + id)
        .attemptNo(1)
        .resourceType(resourceType)
        .resourceId(resourceId)
        .actionType(actionType)
        .requesterUserId(7L)
        .mode(ExecutionMode.EIP7702)
        .status(ExecutionIntentStatus.CONFIRMED)
        .payloadHash("payload-" + id)
        .payloadSnapshotJson("{}")
        .authorityAddress("0x1111111111111111111111111111111111111111")
        .authorityNonce(1L)
        .delegateTarget("0x2222222222222222222222222222222222222222")
        .expiresAt(now.plusMinutes(5))
        .authorizationPayloadHash("0xauthorization")
        .executionDigest("0xdigest")
        .reservedSponsorCostWei(java.math.BigInteger.ZERO)
        .sponsorUsageDateKst(LocalDate.of(2026, 5, 17))
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
