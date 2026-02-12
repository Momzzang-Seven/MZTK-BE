package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminActionType;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminTargetType;

public interface RecordWeb3AdminActionAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId,
      Web3AdminActionType actionType,
      Web3AdminTargetType targetType,
      String targetId,
      boolean success,
      Map<String, Object> detail) {}
}
