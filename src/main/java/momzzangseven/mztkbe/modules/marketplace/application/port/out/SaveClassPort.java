package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;

/**
 * Output port for persisting a marketplace class.
 *
 * <p>When the class has a null ID, a new row is inserted; otherwise the existing row is updated.
 */
public interface SaveClassPort {

  /**
   * Save (insert or update) a marketplace class.
   *
   * @param marketplaceClass domain model to persist
   * @return saved domain model with generated ID and timestamps
   */
  MarketplaceClass save(MarketplaceClass marketplaceClass);
}
