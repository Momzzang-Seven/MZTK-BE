package momzzangseven.mztkbe.modules.marketplace.application;

/**
 * Shared pagination constants for the marketplace module.
 *
 * <p>Centralises the default page size so that all listing/query services use the same value
 * without duplicating a magic number.
 */
public final class MarketplacePaginationConstants {

  private MarketplacePaginationConstants() {}

  /**
   * Default number of items per page for marketplace class listing endpoints.
   *
   * <p>Applies to:
   *
   * <ul>
   *   <li>{@link momzzangseven.mztkbe.modules.marketplace.application.service.GetClassesService}
   *   <li>{@link
   *       momzzangseven.mztkbe.modules.marketplace.application.service.GetTrainerClassesService}
   * </ul>
   */
  public static final int DEFAULT_PAGE_SIZE = 20;
}
