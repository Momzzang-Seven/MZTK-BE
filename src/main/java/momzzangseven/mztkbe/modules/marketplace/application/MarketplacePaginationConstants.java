package momzzangseven.mztkbe.modules.marketplace.application;

/**
 * Shared pagination constants for the marketplace module.
 *
 * <p>Centralises the default page size and sort key so that all listing/query services and
 * persistence adapters use the same values without duplicating magic strings or numbers.
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

  /**
   * Default sort key when no explicit sort is provided and location data is unavailable.
   *
   * <p>Both the service layer ({@code GetClassesService#resolveSort}) and the persistence adapter
   * ({@code ClassPersistenceAdapter#resolveEffectiveSort}) must fall back to this value so that
   * future changes to the default only need to be made in one place.
   */
  public static final String DEFAULT_SORT = "RATING";
}
