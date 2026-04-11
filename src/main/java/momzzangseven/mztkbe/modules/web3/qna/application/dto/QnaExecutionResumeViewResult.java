package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

/**
 * Internal QnA resume contract reserved for MOM-282 response wiring.
 *
 * <p>The shape mirrors the future public DTO but intentionally excludes sign request material.
 */
public record QnaExecutionResumeViewResult(
    Resource resource,
    ExecutionIntent executionIntent,
    Execution execution,
    Transaction transaction) {

  public QnaExecutionResumeViewResult {
    if (resource == null) {
      throw new Web3InvalidInputException("resource is required");
    }
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
    if (execution == null) {
      throw new Web3InvalidInputException("execution is required");
    }
  }

  public record Resource(
      QnaExecutionResourceType type, String id, QnaExecutionResourceStatus status) {

    public Resource {
      if (type == null) {
        throw new Web3InvalidInputException("resource.type is required");
      }
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("resource.id is required");
      }
      if (status == null) {
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

  public record Transaction(Long id, String status, String txHash) {

    public Transaction {
      if (id != null && id <= 0) {
        throw new Web3InvalidInputException("transaction.id must be positive");
      }
      if ((id == null) != (status == null)) {
        throw new Web3InvalidInputException("transaction id/status must be provided together");
      }
    }
  }
}
