package momzzangseven.mztkbe.modules.web3.admin.infrastructure.config;

import momzzangseven.mztkbe.global.error.web3.Web3InternalIssuerDisabledException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminFeatureEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnQnaAdminFeatureEnabled
public class QnaAdminFallbackConfig {

  private static final String INTERNAL_ISSUER_DISABLED_MESSAGE =
      "QnA admin execution requires web3.execution.internal.enabled=true and web3.qna.admin.enabled=true";

  @Bean
  @ConditionalOnMissingBean(GetQnaAdminSettlementReviewUseCase.class)
  GetQnaAdminSettlementReviewUseCase getQnaAdminSettlementReviewUseCaseFallback() {
    return query -> {
      requireSettlementReviewQuery(query);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(ForceQnaAdminSettlementUseCase.class)
  ForceQnaAdminSettlementUseCase forceQnaAdminSettlementUseCaseFallback() {
    return command -> {
      requireSettlementCommand(command);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(GetQnaAdminRefundReviewUseCase.class)
  GetQnaAdminRefundReviewUseCase getQnaAdminRefundReviewUseCaseFallback() {
    return query -> {
      requireRefundReviewQuery(query);
      throw internalIssuerDisabled();
    };
  }

  @Bean
  @ConditionalOnMissingBean(ForceQnaAdminRefundUseCase.class)
  ForceQnaAdminRefundUseCase forceQnaAdminRefundUseCaseFallback() {
    return command -> {
      requireRefundCommand(command);
      throw internalIssuerDisabled();
    };
  }

  private static void requireSettlementCommand(ForceQnaAdminSettlementCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private static void requireRefundCommand(ForceQnaAdminRefundCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private static void requireSettlementReviewQuery(GetQnaAdminSettlementReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
  }

  private static void requireRefundReviewQuery(GetQnaAdminRefundReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
  }

  private static Web3InternalIssuerDisabledException internalIssuerDisabled() {
    return new Web3InternalIssuerDisabledException(INTERNAL_ISSUER_DISABLED_MESSAGE);
  }
}
