package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadTrainerStorePort;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Minimal Spring test configuration for {@link ClassPersistenceAdapterSummaryTest}.
 *
 * <p>{@link ClassPersistenceAdapter} depends on {@link com.querydsl.jpa.impl.JPAQueryFactory},
 * {@link LoadClassTagPort}, and {@link LoadTrainerStorePort} — none of which are used by
 * {@code findSummaryProjectionsBySlotIds}. They are provided as Mockito mocks here so that the
 * adapter can be constructed without wiring the full application context.
 */
@TestConfiguration
class ClassPersistenceAdapterSummaryTestConfig {

  @Bean
  JPAQueryFactory jpaQueryFactory(EntityManager em) {
    return new JPAQueryFactory(em);
  }

  @Bean
  LoadClassTagPort loadClassTagPort() {
    return Mockito.mock(LoadClassTagPort.class);
  }

  @Bean
  LoadTrainerStorePort loadTrainerStorePort() {
    return Mockito.mock(LoadTrainerStorePort.class);
  }

  @Bean
  ClassPersistenceAdapter classPersistenceAdapter(
      MarketplaceClassJpaRepository repo,
      LoadClassTagPort tagPort,
      LoadTrainerStorePort storePort,
      JPAQueryFactory queryFactory,
      EntityManager em) {
    return new ClassPersistenceAdapter(repo, tagPort, storePort, queryFactory, em);
  }
}
