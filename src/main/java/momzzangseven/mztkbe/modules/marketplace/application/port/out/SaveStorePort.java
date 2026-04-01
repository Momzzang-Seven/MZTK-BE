package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;

/**
 * Outbound port for persisting store data.
 *
 * <p>Hexagonal Architecture: This is an OUTPUT PORT that defines operations needed by the
 * application layer. Implemented by an adapter in the infrastructure layer, allowing the application
 * layer to remain independent of infrastructure details.
 */
public interface SaveStorePort {

  /**
   * Atomically creates or updates a trainer store.
   *
   * <p><b>Conflict resolution:</b> Uses {@code trainerId} as the unique key. If a store for the
   * same trainerId already exists, all mutable fields are overwritten.
   *
   * <p><b>Postcondition:</b> The returned TrainerStore will have:
   *
   * <ul>
   *   <li>non-null {@code id} (database-generated)
   *   <li>non-null {@code createdAt} (original creation time, never overwritten on update)
   *   <li>non-null {@code updatedAt} (current transaction time)
   * </ul>
   *
   * @param store the store data to persist ({@code id} field is ignored for conflict resolution)
   * @return the persisted store with all fields populated from the database
   * @throws IllegalStateException if the store cannot be read back after persistence
   */
  TrainerStore save(TrainerStore store);
}
