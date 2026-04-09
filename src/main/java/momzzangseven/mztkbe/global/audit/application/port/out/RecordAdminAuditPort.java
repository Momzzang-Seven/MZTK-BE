package momzzangseven.mztkbe.global.audit.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditSource;

public interface RecordAdminAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId,
      AuditSource source,
      String actionType,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> detail) {

    public AuditCommand {
      if (operatorId == null || operatorId <= 0) {
        throw new IllegalArgumentException("operatorId must be positive");
      }
      if (source == null) {
        throw new IllegalArgumentException("source is required");
      }
      if (actionType == null || actionType.isBlank()) {
        throw new IllegalArgumentException("actionType is required");
      }
      if (targetType == null || targetType.isBlank()) {
        throw new IllegalArgumentException("targetType is required");
      }
    }
  }
}
