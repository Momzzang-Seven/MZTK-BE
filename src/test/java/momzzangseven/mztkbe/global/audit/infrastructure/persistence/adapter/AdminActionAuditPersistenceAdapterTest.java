package momzzangseven.mztkbe.global.audit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity.AdminActionAuditEntity;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.repository.AdminActionAuditJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminActionAuditPersistenceAdapter 단위 테스트")
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
  @DisplayName("정상 AuditCommand 가 주어지면, record 는 매핑된 필드와 직렬화된 detailJson 으로 엔티티를 저장한다")
  void record_savesEntityWithMappedFieldsAndSerializedDetail() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            5L,
            "TRANSACTION_MARK_SUCCEEDED",
            AuditTargetType.WEB3_TRANSACTION,
            "tx-22",
            true,
            Map.of("k", "v"));

    adapter.record(command);

    ArgumentCaptor<AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    AdminActionAuditEntity saved = captor.getValue();
    assertThat(saved.getOperatorId()).isEqualTo(5L);
    assertThat(saved.getActionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(saved.getTargetType()).isEqualTo("WEB3_TRANSACTION");
    assertThat(saved.getTargetId()).isEqualTo("tx-22");
    assertThat(saved.isSuccess()).isTrue();
    assertThat(saved.getDetailJson()).isEqualTo("{\"k\":\"v\"}");
    assertThat(saved.getCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("detail 이 빈 Map 이면, record 는 detailJson 을 null 로 저장한다")
  void record_savesNullDetailJson_whenDetailIsEmpty() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            1L, "TREASURY_KEY_PROVISION", AuditTargetType.TREASURY_KEY, "u-1", true, Map.of());

    adapter.record(command);

    ArgumentCaptor<AdminActionAuditEntity> captor =
        ArgumentCaptor.forClass(AdminActionAuditEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDetailJson()).isNull();
  }

  @Test
  @DisplayName("ObjectMapper 가 직렬화 도중 예외를 던지면, record 는 sentinel detailJson 으로 엔티티를 저장한다")
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
            "TREASURY_KEY_PROVISION",
            AuditTargetType.TREASURY_KEY,
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
  @DisplayName("operatorId 가 0 이하이면, AuditCommand 생성자는 IllegalArgumentException 을 던진다")
  void auditCommand_rejectsNonPositiveOperatorId() {
    assertThatThrownBy(
            () ->
                new RecordAdminAuditPort.AuditCommand(
                    0L, "X", AuditTargetType.TREASURY_KEY, null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operatorId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("actionType 이 null/빈문자열/공백이면, AuditCommand 생성자는 IllegalArgumentException 을 던진다")
  void auditCommand_rejectsNullOrBlankActionType(String actionType) {
    assertThatThrownBy(
            () ->
                new RecordAdminAuditPort.AuditCommand(
                    1L, actionType, AuditTargetType.TREASURY_KEY, null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actionType");
  }

  @Test
  @DisplayName("targetType 이 null 이면, AuditCommand 생성자는 IllegalArgumentException 을 던진다")
  void auditCommand_rejectsNullTargetType() {
    assertThatThrownBy(
            () -> new RecordAdminAuditPort.AuditCommand(1L, "X", null, null, true, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetType");
  }

  @Test
  @DisplayName("nullable 필드(targetId/detail) 가 null 이어도, AuditCommand 는 값을 보존한다")
  void auditCommand_preservesNullableFields() {
    RecordAdminAuditPort.AuditCommand command =
        new RecordAdminAuditPort.AuditCommand(
            1L, "X", AuditTargetType.WEB3_TRANSACTION, null, true, null);

    assertThat(command.targetType()).isEqualTo(AuditTargetType.WEB3_TRANSACTION);
    assertThat(command.targetId()).isNull();
    assertThat(command.detail()).isNull();
  }

  @Test
  @DisplayName("record() 메서드는 외부 트랜잭션과 분리되도록 @Transactional(REQUIRES_NEW) 로 어노테이션 되어 있다")
  void record_isAnnotatedWithRequiresNewTransaction() throws NoSuchMethodException {
    Method recordMethod =
        AdminActionAuditPersistenceAdapter.class.getMethod(
            "record", RecordAdminAuditPort.AuditCommand.class);

    Transactional transactional = recordMethod.getAnnotation(Transactional.class);

    assertThat(transactional)
        .as("record() must be annotated with @Transactional so a new transaction is started")
        .isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
