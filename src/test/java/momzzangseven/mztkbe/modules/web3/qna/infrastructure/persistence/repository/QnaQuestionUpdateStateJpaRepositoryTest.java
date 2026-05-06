package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository;

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
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository.Web3ExecutionIntentJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionUpdateStateEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("QnaQuestionUpdateStateJpaRepository integration test")
class QnaQuestionUpdateStateJpaRepositoryTest {

  @Autowired private QnaQuestionUpdateStateJpaRepository stateRepository;
  @Autowired private Web3ExecutionIntentJpaRepository executionIntentRepository;

  @Test
  @DisplayName("reconciliation candidates skip non-confirmed intents before confirmed rows")
  void findConfirmedIntentBoundForReconciliationSkipsNonConfirmedFrontRows() {
    for (int i = 0; i < 100; i++) {
      String publicId = "pending-intent-" + i;
      saveState(1000L + i, i + 1L, publicId, at(9, i));
      saveIntent(publicId, ExecutionIntentStatus.AWAITING_SIGNATURE, at(9, i));
    }
    saveState(2000L, 200L, "confirmed-intent", at(11, 0));
    saveIntent("confirmed-intent", ExecutionIntentStatus.CONFIRMED, at(11, 0));

    List<QnaQuestionUpdateStateEntity> result =
        stateRepository.findConfirmedIntentBoundForReconciliation(
            QnaQuestionUpdateStateStatus.INTENT_BOUND,
            ExecutionIntentStatus.CONFIRMED,
            ExecutionActionType.QNA_QUESTION_UPDATE,
            PageRequest.of(0, 1));

    assertThat(result)
        .extracting(QnaQuestionUpdateStateEntity::getExecutionIntentPublicId)
        .containsExactly("confirmed-intent");
  }

  private void saveState(
      Long postId, Long version, String intentPublicId, LocalDateTime updatedAt) {
    stateRepository.save(
        QnaQuestionUpdateStateEntity.builder()
            .postId(postId)
            .requesterUserId(7L)
            .updateVersion(version)
            .updateToken("token-" + version)
            .expectedQuestionHash("0x" + "a".repeat(64))
            .executionIntentPublicId(intentPublicId)
            .status(QnaQuestionUpdateStateStatus.INTENT_BOUND)
            .preparationRetryable(true)
            .createdAt(updatedAt)
            .updatedAt(updatedAt)
            .build());
  }

  private void saveIntent(String publicId, ExecutionIntentStatus status, LocalDateTime createdAt) {
    executionIntentRepository.save(
        Web3ExecutionIntentEntity.builder()
            .publicId(publicId)
            .rootIdempotencyKey("root-" + publicId)
            .attemptNo(1)
            .resourceType(ExecutionResourceType.QUESTION)
            .resourceId("question-" + publicId)
            .actionType(ExecutionActionType.QNA_QUESTION_UPDATE)
            .requesterUserId(7L)
            .mode(ExecutionMode.EIP7702)
            .status(status)
            .payloadHash("0x" + "b".repeat(64))
            .payloadSnapshotJson("{\"payload\":true}")
            .authorityAddress("0x" + "1".repeat(40))
            .authorityNonce(1L)
            .delegateTarget("0x" + "2".repeat(40))
            .expiresAt(createdAt.plusMinutes(5))
            .authorizationPayloadHash("0x" + "3".repeat(64))
            .executionDigest("0x" + "4".repeat(64))
            .reservedSponsorCostWei(BigInteger.ZERO)
            .sponsorUsageDateKst(LocalDate.of(2026, 4, 12))
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());
  }

  private LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2026, 4, 12, hour, 0).plusMinutes(minute);
  }
}
