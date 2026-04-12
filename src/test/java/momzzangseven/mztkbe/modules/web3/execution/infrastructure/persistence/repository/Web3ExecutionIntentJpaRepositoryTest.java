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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Web3ExecutionIntentJpaRepository integration test")
class Web3ExecutionIntentJpaRepositoryTest {

  @Autowired private Web3ExecutionIntentJpaRepository repository;

  @Test
  @DisplayName("findLatestByResource returns newest row by createdAt instead of attemptNo")
  void findLatestByResource_ordersByCreatedAt() {
    persistIntent("intent-old", "root-a", 3, 7L, ExecutionResourceType.QUESTION, "101", at(10, 0));
    persistIntent("intent-new", "root-b", 1, 7L, ExecutionResourceType.QUESTION, "101", at(11, 0));

    List<Web3ExecutionIntentEntity> result =
        repository.findLatestByResource(
            ExecutionResourceType.QUESTION, "101", PageRequest.of(0, 1));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPublicId()).isEqualTo("intent-new");
  }

  @Test
  @DisplayName(
      "findLatestByRequesterAndResource also respects requester filter and createdAt ordering")
  void findLatestByRequesterAndResource_filtersByRequester() {
    persistIntent(
        "intent-other-user", "root-o", 5, 8L, ExecutionResourceType.ANSWER, "201", at(12, 0));
    persistIntent("intent-old", "root-a", 4, 7L, ExecutionResourceType.ANSWER, "201", at(10, 0));
    persistIntent("intent-new", "root-b", 1, 7L, ExecutionResourceType.ANSWER, "201", at(11, 0));

    List<Web3ExecutionIntentEntity> result =
        repository.findLatestByRequesterAndResource(
            7L, ExecutionResourceType.ANSWER, "201", PageRequest.of(0, 1));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPublicId()).isEqualTo("intent-new");
  }

  private void persistIntent(
      String publicId,
      String rootIdempotencyKey,
      int attemptNo,
      Long requesterUserId,
      ExecutionResourceType resourceType,
      String resourceId,
      LocalDateTime createdAt) {
    repository.save(
        Web3ExecutionIntentEntity.builder()
            .publicId(publicId)
            .rootIdempotencyKey(rootIdempotencyKey)
            .attemptNo(attemptNo)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .actionType(ExecutionActionType.TRANSFER_SEND)
            .requesterUserId(requesterUserId)
            .mode(ExecutionMode.EIP7702)
            .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
            .payloadHash("0x" + "a".repeat(64))
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
    return LocalDateTime.of(2026, 4, 12, hour, minute);
  }
}
