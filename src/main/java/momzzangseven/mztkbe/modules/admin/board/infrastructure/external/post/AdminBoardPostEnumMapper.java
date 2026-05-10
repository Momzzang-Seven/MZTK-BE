package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import java.util.Objects;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

final class AdminBoardPostEnumMapper {

  private AdminBoardPostEnumMapper() {}

  static AdminBoardPostPublicationStatus toAdminPublicationStatus(
      PostPublicationStatus publicationStatus) {
    Objects.requireNonNull(publicationStatus, "post publicationStatus must not be null");
    return switch (publicationStatus) {
      case PENDING -> AdminBoardPostPublicationStatus.PENDING;
      case VISIBLE -> AdminBoardPostPublicationStatus.VISIBLE;
      case FAILED -> AdminBoardPostPublicationStatus.FAILED;
    };
  }

  static PostPublicationStatus toPostPublicationStatus(
      AdminBoardPostPublicationStatus publicationStatus) {
    if (publicationStatus == null) {
      return null;
    }
    return switch (publicationStatus) {
      case PENDING -> PostPublicationStatus.PENDING;
      case VISIBLE -> PostPublicationStatus.VISIBLE;
      case FAILED -> PostPublicationStatus.FAILED;
    };
  }

  static AdminBoardPostModerationStatus toAdminModerationStatus(
      PostModerationStatus moderationStatus) {
    Objects.requireNonNull(moderationStatus, "post moderationStatus must not be null");
    return switch (moderationStatus) {
      case NORMAL -> AdminBoardPostModerationStatus.NORMAL;
      case BLOCKED -> AdminBoardPostModerationStatus.BLOCKED;
    };
  }

  static PostModerationStatus toPostModerationStatus(
      AdminBoardPostModerationStatus moderationStatus) {
    if (moderationStatus == null) {
      return null;
    }
    return switch (moderationStatus) {
      case NORMAL -> PostModerationStatus.NORMAL;
      case BLOCKED -> PostModerationStatus.BLOCKED;
    };
  }

  static AdminBoardPostType toAdminPostType(PostType type) {
    Objects.requireNonNull(type, "post type must not be null");
    return switch (type) {
      case FREE -> AdminBoardPostType.FREE;
      case QUESTION -> AdminBoardPostType.QUESTION;
    };
  }

  static AdminBoardType toAdminBoardType(PostType type) {
    Objects.requireNonNull(type, "post type must not be null");
    return switch (type) {
      case FREE -> AdminBoardType.FREE;
      case QUESTION -> AdminBoardType.QUESTION;
    };
  }

  static PostType toPostType(AdminBoardPostType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case FREE -> PostType.FREE;
      case QUESTION -> PostType.QUESTION;
    };
  }

  static AdminBoardPostStatus toAdminPostStatus(PostStatus status) {
    Objects.requireNonNull(status, "post status must not be null");
    return switch (status) {
      case OPEN -> AdminBoardPostStatus.OPEN;
      case PENDING_ACCEPT -> AdminBoardPostStatus.PENDING_ACCEPT;
      case PENDING_ADMIN_REFUND -> AdminBoardPostStatus.PENDING_ADMIN_REFUND;
      case RESOLVED -> AdminBoardPostStatus.RESOLVED;
    };
  }

  static PostStatus toPostStatus(AdminBoardPostStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case OPEN -> PostStatus.OPEN;
      case PENDING_ACCEPT -> PostStatus.PENDING_ACCEPT;
      case PENDING_ADMIN_REFUND -> PostStatus.PENDING_ADMIN_REFUND;
      case RESOLVED -> PostStatus.RESOLVED;
    };
  }
}
