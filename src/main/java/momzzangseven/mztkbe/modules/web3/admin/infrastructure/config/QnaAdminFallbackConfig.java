package momzzangseven.mztkbe.modules.web3.admin.infrastructure.config;

import momzzangseven.mztkbe.global.error.web3.Web3InternalIssuerDisabledException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QnaAdminFallbackConfig {

  private static final String INTERNAL_ISSUER_DISABLED_MESSAGE =
      "QnA admin execution requires web3.execution.internal-issuer.enabled=true";

  @Bean
  @ConditionalOnMissingBean(CalculateQnaAdminSettlementReviewUseCase.class)
  CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCaseFallback() {
    return query -> {
      requireSettlementReviewQuery(query);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(ExecuteQnaAdminSettlementUseCase.class)
  ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCaseFallback() {
    return command -> {
      requireSettlementCommand(command);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(CalculateQnaAdminRefundReviewUseCase.class)
  CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCaseFallback() {
    return query -> {
      requireRefundReviewQuery(query);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(ExecuteQnaAdminRefundUseCase.class)
  ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCaseFallback() {
    return command -> {
      requireRefundCommand(command);
      throw internalIssuerDisabled();
    };
  }

  private static void requireSettlementCommand(ExecuteQnaAdminSettlementCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private static void requireRefundCommand(ExecuteQnaAdminRefundCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private static void requireSettlementReviewQuery(CalculateQnaAdminSettlementReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
  }

  private static void requireRefundReviewQuery(CalculateQnaAdminRefundReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
  }

  private static Web3InternalIssuerDisabledException internalIssuerDisabled() {
    return new Web3InternalIssuerDisabledException(INTERNAL_ISSUER_DISABLED_MESSAGE);
  }
}
