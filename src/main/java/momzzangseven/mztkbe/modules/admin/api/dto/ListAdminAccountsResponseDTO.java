package momzzangseven.mztkbe.modules.admin.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;

/** Response DTO for listing admin accounts. */
public record ListAdminAccountsResponseDTO(List<AdminAccountSummaryDTO> admins) {

  public static ListAdminAccountsResponseDTO from(List<AdminAccountSummary> summaries) {
    List<AdminAccountSummaryDTO> dtos =
        summaries.stream().map(AdminAccountSummaryDTO::from).toList();
    return new ListAdminAccountsResponseDTO(dtos);
  }
}
