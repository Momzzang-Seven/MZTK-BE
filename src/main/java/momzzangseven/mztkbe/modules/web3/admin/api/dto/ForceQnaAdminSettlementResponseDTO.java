package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public record ForceQnaAdminSettlementResponseDTO(
    QnaExecutionIntentResult.Resource resource,
    String actionType,
    QnaExecutionIntentResult.ExecutionIntent executionIntent,
    Execution execution,
    boolean existing) {

  private static final String AUTHORITY_MODEL = "SERVER_RELAYER_ONLY";

  public static ForceQnaAdminSettlementResponseDTO from(QnaExecutionIntentResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new ForceQnaAdminSettlementResponseDTO(
        result.resource(),
        result.actionType(),
        result.executionIntent(),
        new Execution(result.execution().mode(), false, AUTHORITY_MODEL),
        result.existing());
  }

  public record Execution(String mode, boolean requiresUserSignature, String authorityModel) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new Web3InvalidInputException("execution.mode is required");
      }
      if (authorityModel == null || authorityModel.isBlank()) {
        throw new Web3InvalidInputException("execution.authorityModel is required");
      }
    }
  }
}
