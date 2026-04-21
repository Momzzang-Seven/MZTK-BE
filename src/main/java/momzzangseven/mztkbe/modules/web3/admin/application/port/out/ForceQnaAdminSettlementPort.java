package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementResult;

public interface ForceQnaAdminSettlementPort {

  ForceQnaAdminSettlementResult settle(Long operatorId, Long postId, Long answerId);
}
