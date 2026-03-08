package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.level.LevelUpAlreadyProcessedException;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelUpHistoryEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.LevelUpHistoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LevelUpHistoryPersistenceAdapterTest {

  @Mock private LevelUpHistoryJpaRepository levelUpHistoryJpaRepository;
  @Mock private EntityManager entityManager;
  @Mock private TypedQuery<LevelUpHistoryEntity> typedQuery;

  @InjectMocks private LevelUpHistoryPersistenceAdapter adapter;

  @Test
  void saveLevelUpHistory_shouldConvertEntityToDomain() {
    LevelUpHistory history =
        LevelUpHistory.initial(1L, 10L, 1, 2, 100, 5, LocalDateTime.of(2026, 2, 28, 10, 0));
    LevelUpHistoryEntity savedEntity =
        LevelUpHistoryEntity.builder()
            .id(99L)
            .userId(1L)
            .levelPolicyId(10L)
            .fromLevel(1)
            .toLevel(2)
            .spentXp(100)
            .rewardMztk(5)
            .createdAt(LocalDateTime.of(2026, 2, 28, 10, 0))
            .build();
    when(levelUpHistoryJpaRepository.saveAndFlush(any(LevelUpHistoryEntity.class)))
        .thenReturn(savedEntity);

    LevelUpHistory result = adapter.saveLevelUpHistory(history);

    assertThat(result.getId()).isEqualTo(99L);
    assertThat(result.getToLevel()).isEqualTo(2);
  }

  @Test
  void saveLevelUpHistory_shouldThrowAlreadyProcessedOnUniqueViolation() {
    when(levelUpHistoryJpaRepository.saveAndFlush(any(LevelUpHistoryEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(
            () ->
                adapter.saveLevelUpHistory(
                    LevelUpHistory.initial(
                        1L, 10L, 1, 2, 100, 5, LocalDateTime.of(2026, 2, 28, 10, 0))))
        .isInstanceOf(LevelUpAlreadyProcessedException.class);
  }

  @Test
  void loadLevelUpHistories_shouldUseFetchSizePlusOne() {
    when(entityManager.createQuery(any(), eq(LevelUpHistoryEntity.class))).thenReturn(typedQuery);
    when(typedQuery.setParameter("userId", 1L)).thenReturn(typedQuery);
    when(typedQuery.setFirstResult(2)).thenReturn(typedQuery);
    when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
    when(typedQuery.getResultList())
        .thenReturn(
            List.of(
                LevelUpHistoryEntity.builder()
                    .id(1L)
                    .userId(1L)
                    .levelPolicyId(10L)
                    .fromLevel(1)
                    .toLevel(2)
                    .spentXp(100)
                    .rewardMztk(3)
                    .createdAt(LocalDateTime.of(2026, 2, 28, 10, 0))
                    .build()));

    List<LevelUpHistory> loaded = adapter.loadLevelUpHistories(1L, 1, 2);

    assertThat(loaded).hasSize(1);
    verify(typedQuery).setFirstResult(2);
    verify(typedQuery).setMaxResults(3);
  }
}
