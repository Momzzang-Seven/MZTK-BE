package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ListTreasuryWalletsQuery;

/**
 * Query-string binding for {@code GET /admin/web3/treasury-keys}. Holds the raw status string from
 * the HTTP layer; conversion to the typed {@link ListTreasuryWalletsQuery} (and therefore to the
 * domain enum) happens inside {@link ListTreasuryWalletsQuery#fromStatusName(String)}, keeping the
 * controller free of any {@code domain/vo} import.
 *
 * @param status canonical lifecycle name ({@code ACTIVE}, {@code DISABLED}, {@code ARCHIVED}) or
 *     {@code null} / blank to request the full list; case-insensitive
 */
public record ListTreasuryWalletsRequestDTO(String status) {

  /**
   * Build the application-layer query. Delegates parsing to {@link
   * ListTreasuryWalletsQuery#fromStatusName(String)} so the api layer never references the
   * underlying {@code TreasuryWalletStatus} enum.
   */
  public ListTreasuryWalletsQuery toQuery() {
    return ListTreasuryWalletsQuery.fromStatusName(status);
  }
}
