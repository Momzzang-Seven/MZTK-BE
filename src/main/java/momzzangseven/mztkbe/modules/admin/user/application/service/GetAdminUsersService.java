package momzzangseven.mztkbe.modules.admin.user.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserSortKey;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.GetAdminUsersUseCase;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserCommentCountsPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserPostCountsPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserStatusesPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUsersPort;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin user-management list reads. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAdminUsersService implements GetAdminUsersUseCase {

  private final LoadAdminUsersPort loadAdminUsersPort;
  private final LoadAdminUserStatusesPort loadAdminUserStatusesPort;
  private final LoadAdminUserPostCountsPort loadAdminUserPostCountsPort;
  private final LoadAdminUserCommentCountsPort loadAdminUserCommentCountsPort;

  @Override
  @AdminOnly(
      actionType = "ADMIN_USERS_VIEW",
      targetType = AuditTargetType.USER_ACCOUNT,
      operatorId = "#command.operatorUserId",
      targetId = "'users'")
  public Page<AdminUserListItemResult> execute(GetAdminUsersCommand command) {
    java.util.Set<Long> candidateUserIds = resolveCandidateUserIds(command.status());
    if (candidateUserIds != null && candidateUserIds.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }
    if (supportsProfilePagedSort(command.sortKey())) {
      return executeProfilePaged(command, candidateUserIds);
    }

    java.util.List<LoadAdminUsersPort.AdminUserProfileView> profiles =
        loadAdminUsersPort.load(
            new LoadAdminUsersPort.AdminUserProfileQuery(
                command.search(), command.role(), candidateUserIds));
    if (profiles.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }

    java.util.List<Long> userIds =
        profiles.stream().map(LoadAdminUsersPort.AdminUserProfileView::userId).toList();
    java.util.Map<Long, AdminUserAccountStatus> statusByUserId =
        loadAdminUserStatusesPort.load(userIds, command.status());
    java.util.Map<Long, Long> postCounts = loadAdminUserPostCountsPort.load(userIds);
    java.util.Map<Long, Long> commentCounts = loadAdminUserCommentCountsPort.load(userIds);

    java.util.List<AdminUserListItemResult> combined =
        profiles.stream()
            .map(
                profile ->
                    new AdminUserListItemResult(
                        profile.userId(),
                        profile.nickname(),
                        profile.role(),
                        profile.email(),
                        profile.joinedAt(),
                        statusByUserId.get(profile.userId()),
                        postCounts.getOrDefault(profile.userId(), 0L),
                        commentCounts.getOrDefault(profile.userId(), 0L)))
            .filter(item -> item.status() != null)
            .sorted(buildComparator(command.sortKey()))
            .toList();

    long offset = (long) command.page() * command.size();
    if (offset >= combined.size()) {
      return new PageImpl<>(
          java.util.List.of(), PageRequest.of(command.page(), command.size()), combined.size());
    }
    int fromIndex = (int) offset;
    int toIndex = Math.min(fromIndex + command.size(), combined.size());
    return new PageImpl<>(
        combined.subList(fromIndex, toIndex),
        PageRequest.of(command.page(), command.size()),
        combined.size());
  }

  private Page<AdminUserListItemResult> executeProfilePaged(
      GetAdminUsersCommand command, java.util.Set<Long> candidateUserIds) {
    Page<LoadAdminUsersPort.AdminUserProfileView> profilePage =
        loadAdminUsersPort.loadPage(
            new LoadAdminUsersPort.AdminUserProfilePageQuery(
                command.search(),
                command.role(),
                candidateUserIds,
                command.page(),
                command.size(),
                command.sortKey()));
    if (profilePage.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }

    java.util.List<Long> userIds =
        profilePage.getContent().stream()
            .map(LoadAdminUsersPort.AdminUserProfileView::userId)
            .toList();
    java.util.Map<Long, AdminUserAccountStatus> statusByUserId =
        loadAdminUserStatusesPort.load(userIds, command.status());
    java.util.Map<Long, Long> postCounts = loadAdminUserPostCountsPort.load(userIds);
    java.util.Map<Long, Long> commentCounts = loadAdminUserCommentCountsPort.load(userIds);

    java.util.List<AdminUserListItemResult> items =
        profilePage.getContent().stream()
            .map(
                profile ->
                    new AdminUserListItemResult(
                        profile.userId(),
                        profile.nickname(),
                        profile.role(),
                        profile.email(),
                        profile.joinedAt(),
                        statusByUserId.get(profile.userId()),
                        postCounts.getOrDefault(profile.userId(), 0L),
                        commentCounts.getOrDefault(profile.userId(), 0L)))
            .filter(item -> item.status() != null)
            .toList();

    return new PageImpl<>(
        items, PageRequest.of(command.page(), command.size()), profilePage.getTotalElements());
  }

  private java.util.Set<Long> resolveCandidateUserIds(AdminUserAccountStatus status) {
    if (status == null) {
      return null;
    }
    return loadAdminUserStatusesPort.load(null, status).keySet();
  }

  private boolean supportsProfilePagedSort(AdminUserSortKey sortKey) {
    return sortKey == AdminUserSortKey.JOINED_AT
        || sortKey == AdminUserSortKey.USER_ID
        || sortKey == AdminUserSortKey.NICKNAME
        || sortKey == AdminUserSortKey.ROLE;
  }

  private java.util.Comparator<AdminUserListItemResult> buildComparator(AdminUserSortKey sortKey) {
    java.util.Comparator<AdminUserListItemResult> comparator =
        switch (sortKey) {
          case JOINED_AT ->
              java.util.Comparator.comparing(AdminUserListItemResult::joinedAt).reversed();
          case USER_ID ->
              java.util.Comparator.comparing(AdminUserListItemResult::userId).reversed();
          case NICKNAME ->
              java.util.Comparator.comparing(
                  AdminUserListItemResult::nickname,
                  java.util.Comparator.nullsLast(String::compareToIgnoreCase));
          case ROLE -> java.util.Comparator.comparing(AdminUserListItemResult::role);
          case STATUS -> java.util.Comparator.comparing(AdminUserListItemResult::status);
          case POST_COUNT ->
              java.util.Comparator.comparing(AdminUserListItemResult::postCount).reversed();
          case COMMENT_COUNT ->
              java.util.Comparator.comparing(AdminUserListItemResult::commentCount).reversed();
        };
    if (sortKey == AdminUserSortKey.USER_ID) {
      return comparator;
    }
    return comparator.thenComparing(
        AdminUserListItemResult::userId, java.util.Comparator.reverseOrder());
  }

  private Page<AdminUserListItemResult> emptyPage(int page, int size) {
    return new PageImpl<>(java.util.List.of(), PageRequest.of(page, size), 0);
  }
}
