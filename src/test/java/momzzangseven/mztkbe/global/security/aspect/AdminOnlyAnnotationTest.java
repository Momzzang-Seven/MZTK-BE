package momzzangseven.mztkbe.global.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.service.BanAdminBoardCommentService;
import momzzangseven.mztkbe.modules.admin.board.application.service.BanAdminBoardPostService;
import momzzangseven.mztkbe.modules.admin.board.application.service.GetAdminBoardCommentsService;
import momzzangseven.mztkbe.modules.admin.board.application.service.GetAdminBoardPostCommentsService;
import momzzangseven.mztkbe.modules.admin.board.application.service.GetAdminBoardPostsService;
import momzzangseven.mztkbe.modules.admin.board.application.service.UnblockAdminBoardPostService;
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

  private static final List<String> RESERVED_AUDIT_DETAIL_KEYS =
      List.of("method", "arguments", "failureReason", "detailEvaluationError");

  @Test
  @DisplayName("관리자 게시글 목록 조회는 admin guard는 유지하되 audit=false 로 설정한다")
  void getAdminBoardPostsService_isGuardedWithoutAudit() throws NoSuchMethodException {
    AdminOnly annotation =
        GetAdminBoardPostsService.class
            .getMethod("execute", GetAdminBoardPostsCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_POSTS_VIEW");
    assertThat(annotation.audit()).isFalse();
  }

  @Test
  @DisplayName("관리자 전역 댓글 검색은 admin guard는 유지하되 audit=false 로 설정한다")
  void getAdminBoardCommentsService_isGuardedWithoutAudit() throws NoSuchMethodException {
    AdminOnly annotation =
        GetAdminBoardCommentsService.class
            .getMethod("execute", GetAdminBoardCommentsCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_COMMENTS_VIEW");
    assertThat(annotation.audit()).isFalse();
  }

  @Test
  @DisplayName("관리자 댓글 목록 조회는 admin guard는 유지하되 audit=false 로 설정한다")
  void getAdminBoardPostCommentsService_isGuardedWithoutAudit() throws NoSuchMethodException {
    AdminOnly annotation =
        GetAdminBoardPostCommentsService.class
            .getMethod("execute", GetAdminBoardPostCommentsCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_POST_COMMENTS_VIEW");
    assertThat(annotation.audit()).isFalse();
  }

  @Test
  @DisplayName("관리자 댓글 ban mutation은 audit=true 기본값을 유지한다")
  void banAdminBoardCommentService_keepsAuditEnabled() throws NoSuchMethodException {
    AdminOnly annotation =
        BanAdminBoardCommentService.class
            .getMethod(
                "execute",
                momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand
                    .class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_COMMENT_BAN");
    assertThat(annotation.audit()).isTrue();
  }

  @Test
  @DisplayName("관리자 게시글 ban mutation은 moderation 결과 audit detail 표현식을 가진다")
  void banAdminBoardPostService_recordsModerationResultDetail() throws NoSuchMethodException {
    AdminOnly annotation =
        BanAdminBoardPostService.class
            .getMethod("execute", BanAdminBoardPostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_POST_BAN");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.POST);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorUserId");
    assertThat(annotation.targetId()).isEqualTo("#command.postId");
    assertThat(annotation.audit()).isTrue();
    assertThat(annotation.detail())
        .containsExactly(
            "reasonCode=#command.reasonCode",
            "reasonDetail=#command.reasonDetail",
            "moderated=#result?.moderated()",
            "publicationStatus=#result?.publicationStatus()",
            "moderationStatus=#result?.moderationStatus()");
    assertThat(detailKeys(annotation)).doesNotContainAnyElementsOf(RESERVED_AUDIT_DETAIL_KEYS);
  }

  @Test
  @DisplayName("관리자 게시글 unblock mutation은 moderation 결과 audit detail 표현식을 가진다")
  void unblockAdminBoardPostService_recordsModerationResultDetail() throws NoSuchMethodException {
    AdminOnly annotation =
        UnblockAdminBoardPostService.class
            .getMethod("execute", UnblockAdminBoardPostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("ADMIN_BOARD_POST_UNBLOCK");
    assertThat(annotation.targetType()).isEqualTo(AuditTargetType.POST);
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorUserId");
    assertThat(annotation.targetId()).isEqualTo("#command.postId");
    assertThat(annotation.audit()).isTrue();
    assertThat(annotation.detail())
        .containsExactly(
            "reasonCode=#command.reasonCode",
            "reasonDetail=#command.reasonDetail",
            "moderated=#result?.moderated()",
            "publicationStatus=#result?.publicationStatus()",
            "moderationStatus=#result?.moderationStatus()");
    assertThat(detailKeys(annotation)).doesNotContainAnyElementsOf(RESERVED_AUDIT_DETAIL_KEYS);
  }

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

  @Test
  @DisplayName("ModeratePostService.blockManagedPost 는 추가 POST_BLOCK audit 을 남기지 않는다")
  void moderatePostServiceBlockManagedPost_isNotAnnotatedWithAdminOnly()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ModeratePostService.class
            .getMethod("blockManagedPost", ModeratePostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNull();
  }

  @Test
  @DisplayName("ModeratePostService.unblockManagedPost 는 추가 POST_UNBLOCK audit 을 남기지 않는다")
  void moderatePostServiceUnblockManagedPost_isNotAnnotatedWithAdminOnly()
      throws NoSuchMethodException {
    AdminOnly annotation =
        ModeratePostService.class
            .getMethod("unblockManagedPost", ModeratePostCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNull();
  }

  private static List<String> detailKeys(AdminOnly annotation) {
    return Arrays.stream(annotation.detail()).map(AdminOnlyAnnotationTest::detailKey).toList();
  }

  private static String detailKey(String detailExpression) {
    int separator = detailExpression.indexOf('=');
    return separator < 0 ? detailExpression : detailExpression.substring(0, separator).trim();
  }
}
