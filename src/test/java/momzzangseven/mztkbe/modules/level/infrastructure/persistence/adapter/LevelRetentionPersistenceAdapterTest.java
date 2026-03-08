package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LevelRetentionPersistenceAdapterTest {

  @Mock private EntityManager entityManager;
  @Mock private Query query;

  @InjectMocks private LevelRetentionPersistenceAdapter adapter;

  @Test
  void deleteUserLevelDataByUserIds_shouldSkipWhenEmpty() {
    adapter.deleteUserLevelDataByUserIds(List.of());

    verify(entityManager, never()).createNativeQuery(anyString());
  }

  @Test
  void deleteUserLevelDataByUserIds_shouldSkipWhenNull() {
    adapter.deleteUserLevelDataByUserIds(null);

    verify(entityManager, never()).createNativeQuery(anyString());
  }

  @Test
  void deleteUserLevelDataByUserIds_shouldExecuteThreeDeleteQueries() {
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(
            org.mockito.ArgumentMatchers.eq("userIds"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(query);

    adapter.deleteUserLevelDataByUserIds(List.of(1L, 2L));

    verify(entityManager, org.mockito.Mockito.times(3)).createNativeQuery(anyString());
    verify(query, org.mockito.Mockito.times(3)).executeUpdate();
  }

  @Test
  void deleteXpLedgerBefore_shouldValidateArguments() {
    assertThatThrownBy(() -> adapter.deleteXpLedgerBefore(null, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cutoff is required");
    assertThatThrownBy(() -> adapter.deleteXpLedgerBefore(LocalDateTime.now(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize must be > 0");
  }

  @Test
  void deleteXpLedgerBefore_shouldExecuteAndReturnCount() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 2, 1, 0, 0);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter("cutoff", cutoff)).thenReturn(query);
    when(query.setParameter("batchSize", 30)).thenReturn(query);
    when(query.executeUpdate()).thenReturn(11);

    int deleted = adapter.deleteXpLedgerBefore(cutoff, 30);

    assertThat(deleted).isEqualTo(11);
  }

  @Test
  void deleteLevelUpHistoriesBefore_shouldValidateArguments() {
    assertThatThrownBy(() -> adapter.deleteLevelUpHistoriesBefore(null, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cutoff is required");
    assertThatThrownBy(() -> adapter.deleteLevelUpHistoriesBefore(LocalDateTime.now(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize must be > 0");
  }

  @Test
  void deleteLevelUpHistoriesBefore_shouldExecuteAndReturnCount() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 2, 1, 0, 0);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter("cutoff", cutoff)).thenReturn(query);
    when(query.setParameter("batchSize", 50)).thenReturn(query);
    when(query.executeUpdate()).thenReturn(7);

    int deleted = adapter.deleteLevelUpHistoriesBefore(cutoff, 50);

    assertThat(deleted).isEqualTo(7);
  }
}
