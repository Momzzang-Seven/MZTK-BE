package momzzangseven.mztkbe.integration.e2e.support;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.modules.admin.application.port.in.BootstrapSeedAdminsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truncates all mutable tables after each E2E test to provide a clean slate. Excludes Flyway
 * history and static seed reference tables so their contents survive across tests.
 *
 * <p>Relies on PostgreSQL {@code TRUNCATE ... RESTART IDENTITY CASCADE} to resolve FK ordering and
 * reset sequences in a single statement. Only active under the {@code integration} profile.
 */
@Component
@Profile("integration")
public class DatabaseCleaner {

  private static final Logger log = LoggerFactory.getLogger(DatabaseCleaner.class);

  /** Tables whose contents must be preserved across tests. */
  private static final Set<String> EXCLUDED_TABLES =
      Set.of("flyway_schema_history", "level_policies", "xp_policies", "web3_treasury_keys");

  @PersistenceContext private EntityManager entityManager;

  private final ObjectProvider<BootstrapSeedAdminsUseCase> bootstrapSeedAdminsProvider;

  private String truncateStatement;

  public DatabaseCleaner(ObjectProvider<BootstrapSeedAdminsUseCase> bootstrapSeedAdminsProvider) {
    this.bootstrapSeedAdminsProvider = bootstrapSeedAdminsProvider;
  }

  @PostConstruct
  @SuppressWarnings("unchecked")
  void init() {
    List<String> allTables =
        entityManager
            .createNativeQuery(
                "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
            .getResultList();

    String joined =
        allTables.stream()
            .filter(t -> !EXCLUDED_TABLES.contains(t))
            .sorted()
            .collect(Collectors.joining(", "));

    if (joined.isEmpty()) {
      truncateStatement = null;
      log.warn("DatabaseCleaner: no truncatable tables discovered");
      return;
    }

    truncateStatement = "TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE";
    log.info(
        "DatabaseCleaner initialized — {} table(s) will be truncated per test",
        allTables.size() - EXCLUDED_TABLES.size());
  }

  /**
   * Wipes all non-excluded tables. FK constraints are satisfied automatically via {@code CASCADE};
   * identity sequences are reset so tests get deterministic IDs.
   */
  @Transactional
  public void clean() {
    if (truncateStatement == null) {
      return;
    }
    entityManager.createNativeQuery(truncateStatement).executeUpdate();
  }

  /**
   * Re-runs the seed admin bootstrap after a {@link #clean()}. Intended for tests that opt into
   * bootstrap-provisioned admin rows. The underlying use case is idempotent: it only inserts when
   * fewer than the required number of seed admins exist.
   */
  public void reseedBootstrapAdmins() {
    BootstrapSeedAdminsUseCase useCase = bootstrapSeedAdminsProvider.getIfAvailable();
    if (useCase == null) {
      log.warn("reseedBootstrapAdmins called but BootstrapSeedAdminsUseCase is not available");
      return;
    }
    useCase.execute();
  }
}
