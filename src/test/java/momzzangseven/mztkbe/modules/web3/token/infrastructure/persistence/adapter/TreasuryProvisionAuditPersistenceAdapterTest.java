package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryProvisionAuditEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryProvisionAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryProvisionAuditPersistenceAdapterTest {

  @Mock private Web3TreasuryProvisionAuditJpaRepository repository;

  private TreasuryProvisionAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TreasuryProvisionAuditPersistenceAdapter(repository);
  }

  @Test
  void record_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.record(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void record_savesEntity_whenValid() {
    RecordTreasuryProvisionAuditPort.AuditCommand command =
        new RecordTreasuryProvisionAuditPort.AuditCommand(1L, "0x" + "a".repeat(40), true, null);

    adapter.record(command);

    ArgumentCaptor<Web3TreasuryProvisionAuditEntity> captor =
        ArgumentCaptor.forClass(Web3TreasuryProvisionAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getOperatorId()).isEqualTo(1L);
    assertThat(captor.getValue().isSuccess()).isTrue();
  }
}
