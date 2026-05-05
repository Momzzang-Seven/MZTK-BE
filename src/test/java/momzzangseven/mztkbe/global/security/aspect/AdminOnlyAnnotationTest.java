package momzzangseven.mztkbe.global.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.service.ModeratePostService;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.AdminAuditedExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.AdminAuditedExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.MarkTransactionSucceededService;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.ArchiveTreasuryWalletService;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.DisableTreasuryWalletService;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.ProvisionTreasuryKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compile-time / reflection-time guard tests around the {@link AdminOnly} annotation. These tests
 * exist to defend the refactor's core safety property: that the two web3 services route their
 * audits through the correct {@link AuditTargetType} value.
 */
@DisplayName("@AdminOnly 어노테이션 컴파일/리플렉션 가드 테스트")
class AdminOnlyAnnotationTest {

  @Test
  @DisplayName(
      "MarkTransactionSucceededService.execute 를 리플렉션으로 조회하면, "
          + "@AdminOnly(targetType=WEB3_TRANSACTION) 메타데이터가 그대로 부착되어 있다")
  void markTransactionSucceededService_isAnnotatedWithWeb3TransactionTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        MarkTransactionSucceededService.class
            .getMethod("execute", MarkTransactionSucceededCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.WEB3_TRANSACTION);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("#command.transactionId()");
  }

  @Test
  @DisplayName(
      "ProvisionTreasuryKeyService.execute 를 리플렉션으로 조회하면, "
          + "@AdminOnly(targetType=TREASURY_KEY) 메타데이터가 그대로 부착되어 있다")
  void provisionTreasuryKeyService_isAnnotatedWithTreasuryKeyTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ProvisionTreasuryKeyService.class
            .getMethod(
                "execute",
                momzzangseven.mztkbe.modules.web3.treasury.application.dto
                    .ProvisionTreasuryKeyCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TREASURY_KEY_PROVISION");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorUserId()");
    assertThat(annotation.targetId()).isEqualTo("#result != null ? #result.walletAddress() : null");
  }

  @Test
  @DisplayName(
      "DisableTreasuryWalletService.execute 를 리플렉션으로 조회하면, "
          + "@AdminOnly(actionType=TREASURY_KEY_DISABLE) 메타데이터가 그대로 부착되어 있다")
  void disableTreasuryWalletService_isAnnotatedWithTreasuryKeyTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        DisableTreasuryWalletService.class
            .getMethod("execute", DisableTreasuryWalletCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TREASURY_KEY_DISABLE");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorUserId()");
    assertThat(annotation.targetId()).isEqualTo("#result != null ? #result.walletAddress() : null");
  }

  @Test
  @DisplayName(
      "ArchiveTreasuryWalletService.execute 를 리플렉션으로 조회하면, "
          + "@AdminOnly(actionType=TREASURY_KEY_ARCHIVE) 메타데이터가 그대로 부착되어 있다")
  void archiveTreasuryWalletService_isAnnotatedWithTreasuryKeyTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ArchiveTreasuryWalletService.class
            .getMethod("execute", ArchiveTreasuryWalletCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TREASURY_KEY_ARCHIVE");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorUserId()");
    assertThat(annotation.targetId()).isEqualTo("#result != null ? #result.walletAddress() : null");
  }

  @Test
  @DisplayName(
      "QnA admin settlement execute bean method에는 @AdminOnly(targetType=QNA_ESCROW_QUESTION)가 부착된다")
  void qnaAdminSettlementExecuteUseCase_isAnnotatedWithQnaEscrowQuestionTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        AdminAuditedExecuteQnaAdminSettlementUseCase.class
            .getMethod("execute", ExecuteQnaAdminSettlementCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("QNA_ADMIN_SETTLE");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.QNA_ESCROW_QUESTION);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("'post:' + #command.postId()");
  }

  @Test
  @DisplayName(
      "QnA admin refund execute bean method에는 @AdminOnly(targetType=QNA_ESCROW_QUESTION)가 부착된다")
  void qnaAdminRefundExecuteUseCase_isAnnotatedWithQnaEscrowQuestionTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        AdminAuditedExecuteQnaAdminRefundUseCase.class
            .getMethod("execute", ExecuteQnaAdminRefundCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("QNA_ADMIN_REFUND");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.QNA_ESCROW_QUESTION);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("'post:' + #command.postId()");
  }

  @Test
  @DisplayName("ModeratePostService.blockPost 에는 POST_MODERATION audit 메타데이터가 부착된다")
  void moderatePostServiceBlockPost_isAnnotatedWithPostModerationTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ModeratePostService.class
            .getMethod("blockPost", ModeratePostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("POST_BLOCK");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.POST_MODERATION);
    assertThat(annotation.operatorId()).isEqualTo("#p0.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("'post:' + #p0.postId()");
  }

  @Test
  @DisplayName("ModeratePostService.unblockPost 에는 POST_MODERATION audit 메타데이터가 부착된다")
  void moderatePostServiceUnblockPost_isAnnotatedWithPostModerationTargetType()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ModeratePostService.class
            .getMethod("unblockPost", ModeratePostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("POST_UNBLOCK");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.POST_MODERATION);
    assertThat(annotation.operatorId()).isEqualTo("#p0.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("'post:' + #p0.postId()");
  }
}
