package momzzangseven.mztkbe.global.audit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditSource;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity.AdminActionAuditEntity;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.repository.AdminActionAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminActionAuditPersistenceAdapterTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-09T12:34:56Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  @Mock private AdminActionAuditJpaRepository repository;

  private AdminActionAuditPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminActionAuditPersistenceAdapter(repository, new ObjectMapper(), FIXED_CLOCK);
  }

  @Test
  void record_savesEntityWithMappedFieldsAndSerializedDetail() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            5L,
            AuditSource.WEB3,
            "TRANSACTION_MARK_SUCCEEDED",
            "WEB3_TRANSACTION",
            "tx-22",
            true,
            Map.of("k", "v"));

    adapter.record(command);

    ArgumentCaptor<AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    AdminActionAuditEntity saved = captor.getValue();
    assertThat(saved.getOperatorId()).isEqualTo(5L);
    assertThat(saved.getSource()).isEqualTo("WEB3");
    assertThat(saved.getActionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(saved.getTargetType()).isEqualTo("WEB3_TRANSACTION");
    assertThat(saved.getTargetId()).isEqualTo("tx-22");
    assertThat(saved.isSuccess()).isTrue();
    assertThat(saved.getDetailJson()).isEqualTo("{\"k\":\"v\"}");
    assertThat(saved.getCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC));
  }

  @Test
  void record_savesNullDetailJson_whenDetailIsEmpty() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            1L, AuditSource.USER, "USER_ROLE_UPDATE", "USER", "u-1", true, Map.of());

    adapter.record(command);

    ArgumentCaptor<AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDetailJson()).isNull();
  }

  @Test
  void record_savesSentinelDetailJson_whenSerializationFails() throws JsonProcessingException {
    ObjectMapper failing = org.mockito.Mockito.mock(ObjectMapper.class);
    when(failing.copy()).thenReturn(failing);
    when(failing.setSerializationInclusion(any())).thenReturn(failing);
    when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
    AdminActionAuditPersistenceAdapter failingAdapter =
        new AdminActionAuditPersistenceAdapter(repository, failing, FIXED_CLOCK);

    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            1L,
            AuditSource.WEB3,
            "TREASURY_KEY_PROVISION",
            "TREASURY_KEY",
            null,
            true,
            Map.of("k", "v"));

    failingAdapter.record(command);

    ArgumentCaptor<AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDetailJson())
        .isEqualTo(AdminActionAuditPersistenceAdapter.SERIALIZATION_ERROR_SENTINEL);
  }

  @Test
  void auditCommand_rejectsNonPositiveOperatorId() {
    assertThatThrownBy(
            () ->
                new RecordAdminAuditPort.AuditCommand(
                    0L, AuditSource.USER, "X", "Y", null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operatorId");
  }

  @Test
  void auditCommand_rejectsNullSource() {
    assertThatThrownBy(
            () -> new RecordAdminAuditPort.AuditCommand(1L, null, "X", "Y", null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");
  }

  @Test
  void auditCommand_rejectsBlankActionType() {
    assertThatThrownBy(
            () ->
                new RecordAdminAuditPort.AuditCommand(
                    1L, AuditSource.USER, " ", "Y", null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actionType");
  }

  @Test
  void auditCommand_rejectsBlankTargetType() {
    assertThatThrownBy(
            () ->
                new RecordAdminAuditPort.AuditCommand(
                    1L, AuditSource.USER, "X", "", null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetType");
  }
}
