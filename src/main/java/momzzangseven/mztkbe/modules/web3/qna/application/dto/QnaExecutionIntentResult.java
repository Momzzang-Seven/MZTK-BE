package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

public record QnaExecutionIntentResult(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequestBundle signRequest,
    boolean existing) {

  public QnaExecutionIntentResult {
    if (resource == null) {
      throw new Web3InvalidInputException("resource is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
    if (execution == null) {
      throw new Web3InvalidInputException("execution is required");
    }
  }

  public static QnaExecutionIntentResult from(
      String actionType, CreateExecutionIntentResult result) {
    return new QnaExecutionIntentResult(
        new Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        actionType,
        new ExecutionIntent(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new Execution(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.existing());
  }

  public record Resource(String type, String id, String status) {

    public Resource {
      if (type == null || type.isBlank()) {
        throw new Web3InvalidInputException("resource.type is required");
      }
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("resource.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("resource.status is required");
      }
    }
  }

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {

    public ExecutionIntent {
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.status is required");
      }
      if (expiresAt == null) {
        throw new Web3InvalidInputException("executionIntent.expiresAt is required");
      }
    }
  }

  public record Execution(String mode, int signCount) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new Web3InvalidInputException("execution.mode is required");
      }
      if (signCount <= 0) {
        throw new Web3InvalidInputException("execution.signCount must be positive");
      }
    }
  }
}
