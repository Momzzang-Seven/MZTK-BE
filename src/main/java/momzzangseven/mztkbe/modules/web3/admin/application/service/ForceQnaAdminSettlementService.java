package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminSettlementPort;

@RequiredArgsConstructor
public class ForceQnaAdminSettlementService implements ForceQnaAdminSettlementUseCase {

  private final ForceQnaAdminSettlementPort forceQnaAdminSettlementPort;

  @Override
  public ForceQnaAdminSettlementResult execute(ForceQnaAdminSettlementCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return forceQnaAdminSettlementPort.settle(
        command.operatorId(), command.postId(), command.answerId());
  }
}
