package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.RecordTransferGuardAuditPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferGuardAuditEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferGuardAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferGuardAuditPersistenceAdapterTest {

  @Mock private Web3TransferGuardAuditJpaRepository repository;

  private TransferGuardAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TransferGuardAuditPersistenceAdapter(repository);
  }

  @Test
  void record_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.record(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void record_savesMappedEntity() {
    RecordTransferGuardAuditPort.AuditCommand command =
        new RecordTransferGuardAuditPort.AuditCommand(
            7L,
            "127.0.0.1",
            DomainReferenceType.QUESTION_REWARD,
            "101",
            "prepare-1",
            TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
            22L,
            23L,
            BigInteger.TEN,
            BigInteger.ONE);

    adapter.record(command);

    ArgumentCaptor<Web3TransferGuardAuditEntity> captor =
        ArgumentCaptor.forClass(Web3TransferGuardAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    assertThat(captor.getValue().getPrepareId()).isEqualTo("prepare-1");
    assertThat(captor.getValue().getReason())
        .isEqualTo(TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH);
  }
}
