package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundResult;

public interface ForceQnaAdminRefundPort {

  ForceQnaAdminRefundResult refund(Long operatorId, Long postId);
}
