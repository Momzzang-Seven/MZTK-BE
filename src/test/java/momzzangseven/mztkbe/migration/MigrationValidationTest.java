package momzzangseven.mztkbe.migration;

import momzzangseven.mztkbe.MztkBeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards Flyway migration correctness on every CI run.
 *
 * <p>Intentionally <strong>not</strong> tagged {@code @Tag("e2e")} so it is included in {@code
 * ./gradlew test} and cannot be bypassed by skipping E2E suites. Booting the Spring context under
 * the {@code integration} profile runs Flyway end-to-end against real PostgreSQL with {@code
 * validate-on-migrate=true}, and Hibernate re-checks every {@code @Entity} against the resulting
 * schema via {@code ddl-auto=validate}. Any checksum mismatch, out-of-order version, missing
 * migration, or entity/column drift fails context startup — which fails this test.
 */
@DisplayName("[Migration] Flyway migrations apply cleanly and match JPA entities")
@ActiveProfiles("integration")
@SpringBootTest(
    classes = MztkBeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "mztk.admin.bootstrap.enabled=false")
class MigrationValidationTest {

  @Test
  void contextLoads() {}
}
