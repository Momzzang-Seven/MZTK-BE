package momzzangseven.mztkbe.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway-JPA 순환 의존성 해결을 위한 설정
 *
 * <p>Spring Boot 3.x에서 Flyway와 JPA EntityManagerFactory 간의 순환 의존성 문제를 해결합니다.
 * Flyway가 EntityManagerFactory보다 먼저 실행되도록 명시적으로 마이그레이션 전략을 정의합니다.
 *
 * <h3>문제 상황:</h3>
 * <pre>
 * FlywayAutoConfiguration → DataSource 필요 → EntityManagerFactory 대기
 * JpaAutoConfiguration → EntityManagerFactory 생성 → Flyway 완료 대기
 * → 순환 의존성 발생!
 * </pre>
 *
 * <h3>해결 방법:</h3>
 * <p>FlywayMigrationStrategy를 명시적으로 정의하여 Flyway가 즉시 실행되도록 보장하고,
 * EntityManagerFactory와의 의존성을 끊습니다.
 *
 * @author MZTK Backend Team
 * @since 1.0
 */
@Configuration
@Profile("prod")  // prod 프로파일에서만 적용
public class FlywayConfiguration {

    /**
     * Flyway 마이그레이션 전략 정의
     *
     * <p>Flyway 마이그레이션을 명시적으로 먼저 실행하여 EntityManagerFactory와의 순환 의존성을 제거합니다.
     *
     * <h3>동작 순서:</h3>
     * <ol>
     *   <li>Flyway Bean 생성</li>
     *   <li>즉시 migrate() 실행 (데이터베이스 마이그레이션)</li>
     *   <li>EntityManagerFactory 초기화 (스키마 검증 없음, ddl-auto: none)</li>
     *   <li>애플리케이션 시작 완료</li>
     * </ol>
     *
     * @return Flyway 마이그레이션을 즉시 실행하는 전략
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Flyway 마이그레이션을 즉시 실행
            // - baseline-on-migrate: true 설정으로 기존 DB도 처리 가능
            // - validate-on-migrate: true 설정으로 마이그레이션 파일 검증
            flyway.migrate();
        };
    }
}
