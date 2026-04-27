package momzzangseven.mztkbe.modules.marketplace.store.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;

/**
 * Outbound port for persisting store data.
 *
 * <p>Hexagonal Architecture: This is an OUTPUT PORT that defines operations needed by the
 * application layer. Implemented by an adapter in the infrastructure layer, allowing the
 * application layer to remain independent of infrastructure details.
 */
public interface SaveStorePort {

  /**
   * Persist a trainer store via standard JPA save.
   *
   * <p>When {@code store.getId()} is null, a new entity is created (INSERT). When non-null, the
   * existing entity is updated (merge). The application service is responsible for determining
   * create-vs-update logic and setting the ID before calling this method.
   *
   * <p><b>Postcondition:</b> The returned TrainerStore will have:
   *
   * <ul>
   *   <li>non-null {@code id} (database-generated or carried over)
   *   <li>non-null {@code createdAt} (managed by Hibernate @CreationTimestamp)
   *   <li>non-null {@code updatedAt} (managed by Hibernate @UpdateTimestamp)
   * </ul>
   *
   * @param store the store data to persist
   * @return the persisted store with all fields populated from the database
   */
  TrainerStore save(TrainerStore store);
}
