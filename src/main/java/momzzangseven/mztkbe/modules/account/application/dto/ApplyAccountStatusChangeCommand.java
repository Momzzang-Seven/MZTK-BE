package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/**
 * Command for applying a single account-status change to the in-memory denylist.
 *
 * <p>This command is event-sourced from a trusted status-change event, so it carries no {@code
 * validate()} and a {@code null} {@code status} is a valid hard-delete signal rather than malformed
 * input.
 *
 * <p>{@code status} MAY be {@code null}: a {@code null} status means the user was hard-deleted /
 * soft-removed and must be removed from the denylist (evict). A non-null status drives put (for a
 * non-ACTIVE status) or evict (for ACTIVE).
 */
public record ApplyAccountStatusChangeCommand(Long userId, AccountStatus status) {}
