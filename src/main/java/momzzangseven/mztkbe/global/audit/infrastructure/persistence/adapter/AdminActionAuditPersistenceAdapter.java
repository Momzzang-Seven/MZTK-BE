package momzzangseven.mztkbe.global.audit.infrastructure.persistence.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity.AdminActionAuditEntity;
import momzzangseven.mztkbe.global.audit.infrastructure.persistence.repository.AdminActionAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists admin action audit records into the unified {@code admin_action_audits} table.
 *
 * <p>Sole {@code RecordAdminAuditPort} implementation. Validation of {@code AuditCommand} happens
 * inside the record's compact constructor; this adapter only maps to the JPA entity and saves it.
 */
@Component
@Slf4j
public class AdminActionAuditPersistenceAdapter implements RecordAdminAuditPort {

  /**
   * Sentinel JSON written to {@code detail_json} when the original detail map cannot be serialized.
   * Lets future readers of {@code admin_action_audits} distinguish "no detail" (null) from "detail
   * was lost during serialization" (this string).
   */
  static final String SERIALIZATION_ERROR_SENTINEL = "{\"_serializationError\":true}";

  private final AdminActionAuditJpaRepository repository;
  private final ObjectMapper auditObjectMapper;
  private final Clock appClock;

  public AdminActionAuditPersistenceAdapter(
      AdminActionAuditJpaRepository repository, ObjectMapper objectMapper, Clock appClock) {
    this.repository = repository;
    this.auditObjectMapper =
        objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.appClock = appClock;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditCommand command) {
    repository.save(
        AdminActionAuditEntity.builder()
            .operatorId(command.operatorId())
            .source(command.source().name())
            .actionType(command.actionType())
            .targetType(command.targetType())
            .targetId(command.targetId())
            .success(command.success())
            .detailJson(toJson(command.detail()))
            .createdAt(OffsetDateTime.now(appClock))
            .build());
  }

  private String toJson(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return auditObjectMapper.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to serialize admin audit detail to JSON; persisting sentinel. detailKeys={}",
          detail.keySet(),
          e);
      return SERIALIZATION_ERROR_SENTINEL;
    }
  }
}
