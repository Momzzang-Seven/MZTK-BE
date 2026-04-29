package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryKmsAuditEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryKmsAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KmsAuditPersistenceAdapterTest {

  @Mock private Web3TreasuryKmsAuditJpaRepository repository;

  private KmsAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new KmsAuditPersistenceAdapter(repository);
  }

  @Test
  void record_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.record(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void record_savesEntity_whenValid() {
    KmsAuditPort.AuditCommand command =
        new KmsAuditPort.AuditCommand(
            7L,
            "reward-treasury",
            "kms-key-1",
            "0x" + "a".repeat(40),
            KmsAuditAction.KMS_DISABLE,
            true,
            null);

    adapter.record(command);

    ArgumentCaptor<Web3TreasuryKmsAuditEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryKmsAuditEntity.class);
    verify(repository).save(captor.capture());
    Web3TreasuryKmsAuditEntity saved = captor.getValue();
    assertThat(saved.getOperatorId()).isEqualTo(7L);
    assertThat(saved.getWalletAlias()).isEqualTo("reward-treasury");
    assertThat(saved.getKmsKeyId()).isEqualTo("kms-key-1");
    assertThat(saved.getActionType()).isEqualTo("KMS_DISABLE");
    assertThat(saved.isSuccess()).isTrue();
    assertThat(saved.getFailureReason()).isNull();
  }

  @Test
  void record_persistsFailureReason_whenSuccessFalse() {
    KmsAuditPort.AuditCommand command =
        new KmsAuditPort.AuditCommand(
            7L,
            "reward-treasury",
            "kms-key-1",
            null,
            KmsAuditAction.KMS_CREATE_ALIAS,
            false,
            "AwsServiceException");

    adapter.record(command);

    ArgumentCaptor<Web3TreasuryKmsAuditEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryKmsAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().isSuccess()).isFalse();
    assertThat(captor.getValue().getFailureReason()).isEqualTo("AwsServiceException");
    assertThat(captor.getValue().getActionType()).isEqualTo("KMS_CREATE_ALIAS");
  }
}
