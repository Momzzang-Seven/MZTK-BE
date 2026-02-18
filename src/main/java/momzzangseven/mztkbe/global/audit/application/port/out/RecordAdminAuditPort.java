package momzzangseven.mztkbe.global.audit.application.port.out;

import java.util.Map;

public interface RecordAdminAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId,
      String actionType,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> detail) {}
}
