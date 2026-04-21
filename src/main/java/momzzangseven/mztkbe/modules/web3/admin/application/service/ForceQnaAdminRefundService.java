package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminRefundPort;

@RequiredArgsConstructor
public class ForceQnaAdminRefundService implements ForceQnaAdminRefundUseCase {

  private final ForceQnaAdminRefundPort forceQnaAdminRefundPort;

  @Override
  public ForceQnaAdminRefundResult execute(ForceQnaAdminRefundCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return forceQnaAdminRefundPort.refund(command.operatorId(), command.postId());
  }
}
