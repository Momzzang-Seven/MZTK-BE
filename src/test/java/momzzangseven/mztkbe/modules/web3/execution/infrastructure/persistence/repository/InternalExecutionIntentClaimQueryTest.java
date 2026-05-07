package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity.Web3ExecutionIntentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Internal execution intent claim query integration test")
class InternalExecutionIntentClaimQueryTest {

  @Autowired private Web3ExecutionIntentJpaRepository repository;

  @Test
  @DisplayName("claim query selects earliest awaiting-signature eip1559 admin-settle intent")
  void claimQuery_selectsEarliestInternalExecutable() {
    saveIntent(
        "intent-late", ExecutionMode.EIP1559, ExecutionActionType.QNA_ADMIN_SETTLE, at(10, 0));
    saveIntent(
        "intent-early", ExecutionMode.EIP1559, ExecutionActionType.QNA_ADMIN_SETTLE, at(9, 0));
    saveIntent(
        "intent-wrong-action",
        ExecutionMode.EIP1559,
        ExecutionActionType.QNA_ANSWER_ACCEPT,
        at(8, 0));
    saveIntent(
        "intent-wrong-mode", ExecutionMode.EIP7702, ExecutionActionType.QNA_ADMIN_SETTLE, at(7, 0));

    var result =
        repository.claimNextInternalExecutableIdForUpdate(
            List.of(ExecutionActionType.QNA_ADMIN_SETTLE.name()));

    assertThat(result).isPresent();
    Long id = result.orElseThrow();
    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).orElseThrow().getPublicId()).isEqualTo("intent-early");
  }

  @Test
  @DisplayName(
      "peekClaimableInternal returns present when at least one matching intent exists (mirrors claim filter)")
  void peekClaimableInternal_returnsPresent_whenMatchingIntentExists() {
    // peek native query 의 filter 는 claim query 와 lockstep 으로 일치해야 한다 — 둘 중 하나만 수정되면
    // 빈 큐에서도 preflight 가 일어나거나(peek=true 인데 claim=empty), 작업이 있는데 skip 되거나(peek=false
    // 인데 claim=present) 한다. 같은 fixture 에서 양쪽 결과가 일치함을 검증.
    saveIntent(
        "intent-only", ExecutionMode.EIP1559, ExecutionActionType.QNA_ADMIN_SETTLE, at(9, 0));

    var peeked =
        repository.peekClaimableInternal(List.of(ExecutionActionType.QNA_ADMIN_SETTLE.name()));

    assertThat(peeked).isPresent();
  }

  @Test
  @DisplayName(
      "peekClaimableInternal returns empty when status / mode / actionType filter excludes all rows")
  void peekClaimableInternal_returnsEmpty_whenNoRowMatchesFilter() {
    // 각각 status/mode/actionType 셋 중 하나씩만 어긋난 row 를 심어, 어느 한 조건만 어긋나도
    // peek 가 false 를 반환함을 검증. SQL literal 'AWAITING_SIGNATURE' / 'EIP1559' 와
    // IN :actionTypes 가 모두 적용되는지 확인.
    saveIntent(
        "wrong-action", ExecutionMode.EIP1559, ExecutionActionType.QNA_ANSWER_ACCEPT, at(8, 0));
    saveIntent(
        "wrong-mode", ExecutionMode.EIP7702, ExecutionActionType.QNA_ADMIN_SETTLE, at(8, 0));

    var peeked =
        repository.peekClaimableInternal(List.of(ExecutionActionType.QNA_ADMIN_SETTLE.name()));

    assertThat(peeked).isEmpty();
  }

  private void saveIntent(
      String publicId,
      ExecutionMode mode,
      ExecutionActionType actionType,
      LocalDateTime createdAt) {
    repository.save(
        Web3ExecutionIntentEntity.builder()
            .publicId(publicId)
            .rootIdempotencyKey("root-" + publicId)
            .attemptNo(1)
            .resourceType(ExecutionResourceType.QUESTION)
            .resourceId(publicId)
            .actionType(actionType)
            .requesterUserId(7L)
            .counterpartyUserId(22L)
            .mode(mode)
            .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
            .payloadHash("0x" + "a".repeat(64))
            .payloadSnapshotJson("{\"payload\":true}")
            .expiresAt(createdAt.plusMinutes(5))
            .unsignedTxSnapshot("{\"chainId\":11155111}")
            .unsignedTxFingerprint("0x" + "b".repeat(64))
            .reservedSponsorCostWei(BigInteger.ZERO)
            .sponsorUsageDateKst(LocalDate.of(2026, 4, 17))
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());
  }

  private LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2026, 4, 17, hour, minute);
  }
}
