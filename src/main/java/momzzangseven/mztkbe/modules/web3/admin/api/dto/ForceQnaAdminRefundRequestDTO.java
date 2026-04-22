package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundCommand;

public record ForceQnaAdminRefundRequestDTO(Long operatorId, Long postId) {

  public static ForceQnaAdminRefundRequestDTO of(Long operatorId, Long postId) {
    return new ForceQnaAdminRefundRequestDTO(operatorId, postId);
  }

  public ForceQnaAdminRefundCommand toCommand() {
    return new ForceQnaAdminRefundCommand(operatorId, postId);
  }
}
