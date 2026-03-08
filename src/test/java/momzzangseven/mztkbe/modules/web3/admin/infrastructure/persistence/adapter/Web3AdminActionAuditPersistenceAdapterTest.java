package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.entity.Web3AdminActionAuditEntity;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.repository.Web3AdminActionAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Web3AdminActionAuditPersistenceAdapterTest {

  @Mock private Web3AdminActionAuditJpaRepository repository;
  @Mock private Web3AdminAuditLogSerializer auditLogSerializer;

  private Web3AdminActionAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new Web3AdminActionAuditPersistenceAdapter(repository, auditLogSerializer);
  }

  @Test
  void record_throws_whenCommandInvalid() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(1L, " ", "WEB3_TRANSACTION", "1", true, Map.of());

    assertThatThrownBy(() -> adapter.record(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("actionType is required");

    verify(repository, never()).save(any());
  }

  @Test
  void record_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.record(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");

    verify(repository, never()).save(any());
  }

  @Test
  void record_throws_whenOperatorIdInvalid() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            0L, "TRANSACTION_MARK_SUCCEEDED", "WEB3_TRANSACTION", "1", true, Map.of());

    assertThatThrownBy(() -> adapter.record(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");

    verify(repository, never()).save(any());
  }

  @Test
  void record_throws_whenTargetTypeBlank() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            1L, "TRANSACTION_MARK_SUCCEEDED", " ", "1", true, Map.of());

    assertThatThrownBy(() -> adapter.record(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("targetType is required");

    verify(repository, never()).save(any());
  }

  @Test
  void record_savesMappedEntity_whenValid() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            5L, "TRANSACTION_MARK_SUCCEEDED", "WEB3_TRANSACTION", "22", true, Map.of("k", "v"));
    when(auditLogSerializer.toJson(command.detail())).thenReturn("{\"k\":\"v\"}");

    adapter.record(command);

    ArgumentCaptor<Web3AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(Web3AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    Web3AdminActionAuditEntity saved = captor.getValue();
    assertThat(saved.getOperatorId()).isEqualTo(5L);
    assertThat(saved.getActionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(saved.getDetailJson()).isEqualTo("{\"k\":\"v\"}");
  }
}
