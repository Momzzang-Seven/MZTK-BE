package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class XpLedgerPersistenceAdapterTest {

  @Mock private XpLedgerJpaRepository xpLedgerJpaRepository;
  @Mock private EntityManager entityManager;
  @Mock private TypedQuery<XpLedgerEntity> typedQuery;

  @InjectMocks private XpLedgerPersistenceAdapter adapter;

  @Test
  void readMethods_shouldDelegateToRepository() {
    LocalDate today = LocalDate.of(2026, 2, 28);
    when(xpLedgerJpaRepository.existsByUserIdAndIdempotencyKey(1L, "key")).thenReturn(true);
    when(xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, today))
        .thenReturn(2);

    assertThat(adapter.existsByUserIdAndIdempotencyKey(1L, "key")).isTrue();
    assertThat(adapter.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, today)).isEqualTo(2);
  }

  @Test
  void loadXpLedgerEntries_shouldUseFetchSizePlusOne() {
    when(entityManager.createQuery(any(), eq(XpLedgerEntity.class))).thenReturn(typedQuery);
    when(typedQuery.setParameter("userId", 1L)).thenReturn(typedQuery);
    when(typedQuery.setFirstResult(0)).thenReturn(typedQuery);
    when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
    when(typedQuery.getResultList())
        .thenReturn(
            List.of(
                XpLedgerEntity.builder()
                    .id(1L)
                    .userId(1L)
                    .type(XpType.CHECK_IN)
                    .xpAmount(10)
                    .earnedOn(LocalDate.of(2026, 2, 28))
                    .occurredAt(LocalDateTime.of(2026, 2, 28, 9, 0))
                    .idempotencyKey("checkin:1:20260228")
                    .sourceRef("src")
                    .createdAt(LocalDateTime.of(2026, 2, 28, 9, 0))
                    .build()));

    List<XpLedgerEntry> loaded = adapter.loadXpLedgerEntries(1L, 0, 2);

    assertThat(loaded).hasSize(1);
    verify(typedQuery).setMaxResults(3);
  }

  @Test
  void trySaveXpLedger_shouldReturnFalseOnDuplicate() {
    XpLedgerEntry entry =
        XpLedgerEntry.builder()
            .userId(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .occurredAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .idempotencyKey("checkin:1:20260228")
            .sourceRef("src")
            .createdAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .build();
    when(xpLedgerJpaRepository.saveAndFlush(any(XpLedgerEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    boolean saved = adapter.trySaveXpLedger(entry);

    assertThat(saved).isFalse();
  }
}
