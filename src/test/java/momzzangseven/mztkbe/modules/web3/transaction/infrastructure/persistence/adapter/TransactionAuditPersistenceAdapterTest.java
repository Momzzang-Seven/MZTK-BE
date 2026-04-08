package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.AuditLogSerializer;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionAuditEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionAuditPersistenceAdapterTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-08T00:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private Web3TransactionAuditJpaRepository repository;
  @Mock private AuditLogSerializer auditLogSerializer;

  private TransactionAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TransactionAuditPersistenceAdapter(repository, auditLogSerializer, FIXED_CLOCK);
  }

  @Test
  void record_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.record(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void record_throws_whenMockedCommandHasNullTransactionId() {
    RecordTransactionAuditPort.AuditCommand command =
        mock(RecordTransactionAuditPort.AuditCommand.class);
    when(command.transactionId()).thenReturn(null);

    assertThatThrownBy(() -> adapter.record(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId is required");
  }

  @Test
  void record_throws_whenMockedCommandHasNullEventType() {
    RecordTransactionAuditPort.AuditCommand command =
        mock(RecordTransactionAuditPort.AuditCommand.class);
    when(command.transactionId()).thenReturn(7L);
    when(command.eventType()).thenReturn(null);

    assertThatThrownBy(() -> adapter.record(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("eventType is required");
  }

  @Test
  void record_savesMappedEntity_whenValid() {
    RecordTransactionAuditPort.AuditCommand command =
        new RecordTransactionAuditPort.AuditCommand(
            7L, Web3TransactionAuditEventType.STATE_CHANGE, "main", Map.of("from", "SIGNED"));
    when(auditLogSerializer.toJson(command.detail())).thenReturn("{\"from\":\"SIGNED\"}");

    adapter.record(command);

    ArgumentCaptor<Web3TransactionAuditEntity> captor =
        ArgumentCaptor.forClass(Web3TransactionAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getWeb3TransactionId()).isEqualTo(7L);
    assertThat(captor.getValue().getEventType()).isEqualTo(Web3TransactionAuditEventType.STATE_CHANGE);
    assertThat(captor.getValue().getDetailJson()).contains("SIGNED");
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
  }
}
