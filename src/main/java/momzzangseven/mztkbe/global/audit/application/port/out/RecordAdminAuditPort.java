package momzzangseven.mztkbe.global.audit.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;

public interface RecordAdminAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId,
      String actionType,
      AuditTargetType targetType,
      String targetId,
      boolean success,
      Map<String, Object> detail) {

    public AuditCommand {
      // null operatorId is accepted when RecordAdminAuditPort is invoked directly, not from
      // @AdminOnly annotation.
      // POST /admin/recovery/reseed is called via .PERMITALL(). so the operatorId can be null.
      if (operatorId != null && operatorId <= 0) {
        throw new IllegalArgumentException("operatorId must be positive when provided");
      }
      if (actionType == null || actionType.isBlank()) {
        throw new IllegalArgumentException("actionType is required");
      }
      if (targetType == null) {
        throw new IllegalArgumentException("targetType is required");
      }
    }
  }
}
