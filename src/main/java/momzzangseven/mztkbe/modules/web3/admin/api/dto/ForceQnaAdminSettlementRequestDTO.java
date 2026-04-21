package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementCommand;

public record ForceQnaAdminSettlementRequestDTO(Long operatorId, Long postId, Long answerId) {

  public static ForceQnaAdminSettlementRequestDTO of(
      Long operatorId, Long postId, Long answerId) {
    return new ForceQnaAdminSettlementRequestDTO(operatorId, postId, answerId);
  }

  public ForceQnaAdminSettlementCommand toCommand() {
    return new ForceQnaAdminSettlementCommand(operatorId, postId, answerId);
  }
}
