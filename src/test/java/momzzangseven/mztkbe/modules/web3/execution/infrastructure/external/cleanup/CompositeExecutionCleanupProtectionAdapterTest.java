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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
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
  @Mock private FilterWalletRegistrationExecutionCleanupCandidatesUseCase walletProtection;
  @Mock private ObjectProvider<FilterQnaExecutionCleanupCandidatesUseCase> qnaProvider;

  @Mock
  private ObjectProvider<FilterMarketplaceExecutionCleanupCandidatesUseCase> marketplaceProvider;

  @Mock
  private ObjectProvider<FilterWalletRegistrationExecutionCleanupCandidatesUseCase> walletProvider;

  @Test
  @DisplayName("QnA와 marketplace 후보를 각각 보호 필터로 라우팅하고 나머지 intent는 삭제 가능하게 둔다")
  void filterDeletableFinalizedIntentIds_routesFeatureSpecificCandidates() {
    List<Long> candidateIds = List.of(1L, 2L, 3L, 4L);
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
                view(3L, ExecutionResourceType.TRANSFER, "303", ExecutionActionType.TRANSFER_SEND),
                view(
                    4L,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    "registration-1",
                    ExecutionActionType.WALLET_ESCROW_APPROVE)));
    given(qnaProvider.getIfAvailable()).willReturn(qnaProtection);
    given(marketplaceProvider.getIfAvailable()).willReturn(marketplaceProtection);
    given(walletProvider.getIfAvailable()).willReturn(walletProtection);
    given(qnaProtection.filterDeletableFinalizedIntentIds(List.of(1L))).willReturn(List.of(1L));
    given(marketplaceProtection.filterDeletableFinalizedIntentIds(List.of(2L)))
        .willReturn(List.of());
    given(
            walletProtection.filterDeletableFinalizedIntentIds(
                List.of(
                    new WalletRegistrationExecutionCleanupCandidate(
                        4L,
                        "intent-4",
                        "registration-1",
                        "WALLET_REGISTRATION",
                        "WALLET_ESCROW_APPROVE"))))
        .willReturn(List.of());
    CompositeExecutionCleanupProtectionAdapter adapter =
        new CompositeExecutionCleanupProtectionAdapter(
            executionIntentPersistencePort, qnaProvider, marketplaceProvider, walletProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(1L, 3L);
  }

  @Test
  @DisplayName("marketplace user/admin action 전체를 marketplace 보호 필터로 라우팅한다")
  void filterDeletableFinalizedIntentIds_routesAllMarketplaceActions() {
    List<Long> candidateIds = List.of(1L, 2L, 3L, 4L, 5L, 6L);
    given(executionIntentPersistencePort.findAllByIdsForUpdate(candidateIds))
        .willReturn(
            List.of(
                view(
                    1L,
                    ExecutionResourceType.ORDER,
                    "1",
                    ExecutionActionType.MARKETPLACE_CLASS_PURCHASE),
                view(
                    2L,
                    ExecutionResourceType.ORDER,
                    "2",
                    ExecutionActionType.MARKETPLACE_CLASS_CANCEL),
                view(
                    3L,
                    ExecutionResourceType.ORDER,
                    "3",
                    ExecutionActionType.MARKETPLACE_CLASS_CONFIRM),
                view(
                    4L,
                    ExecutionResourceType.ORDER,
                    "4",
                    ExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND),
                view(
                    5L,
                    ExecutionResourceType.ORDER,
                    "5",
                    ExecutionActionType.MARKETPLACE_ADMIN_REFUND),
                view(
                    6L,
                    ExecutionResourceType.ORDER,
                    "6",
                    ExecutionActionType.MARKETPLACE_ADMIN_SETTLE)));
    given(marketplaceProvider.getIfAvailable()).willReturn(marketplaceProtection);
    given(marketplaceProtection.filterDeletableFinalizedIntentIds(candidateIds))
        .willReturn(List.of(2L, 4L, 6L));
    CompositeExecutionCleanupProtectionAdapter adapter =
        new CompositeExecutionCleanupProtectionAdapter(
            executionIntentPersistencePort, qnaProvider, marketplaceProvider, walletProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(2L, 4L, 6L);
  }

  @Test
  @DisplayName("feature protection provider가 없으면 해당 feature intent는 삭제하지 않는다")
  void filterDeletableFinalizedIntentIds_failClosedWhenFeatureProtectionMissing() {
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
    CompositeExecutionCleanupProtectionAdapter adapter =
        new CompositeExecutionCleanupProtectionAdapter(
            executionIntentPersistencePort, qnaProvider, marketplaceProvider, walletProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(3L);
  }

  @Test
  @DisplayName("wallet registration approval intent를 wallet 보호 필터로 라우팅한다")
  void filterDeletableFinalizedIntentIds_routesWalletRegistrationApproval() {
    List<Long> candidateIds = List.of(1L, 2L);
    given(executionIntentPersistencePort.findAllByIdsForUpdate(candidateIds))
        .willReturn(
            List.of(
                view(
                    1L,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    "registration-1",
                    ExecutionActionType.WALLET_ESCROW_APPROVE),
                view(
                    2L, ExecutionResourceType.TRANSFER, "303", ExecutionActionType.TRANSFER_SEND)));
    given(walletProvider.getIfAvailable()).willReturn(walletProtection);
    given(
            walletProtection.filterDeletableFinalizedIntentIds(
                List.of(
                    new WalletRegistrationExecutionCleanupCandidate(
                        1L,
                        "intent-1",
                        "registration-1",
                        "WALLET_REGISTRATION",
                        "WALLET_ESCROW_APPROVE"))))
        .willReturn(List.of(1L));
    CompositeExecutionCleanupProtectionAdapter adapter =
        new CompositeExecutionCleanupProtectionAdapter(
            executionIntentPersistencePort, qnaProvider, marketplaceProvider, walletProvider);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(1L, 2L);
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
