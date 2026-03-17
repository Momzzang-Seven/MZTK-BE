package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationRequestPersistenceAdapterTest {

  @Mock private VerificationRequestJpaRepository repository;

  private VerificationRequestPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new VerificationRequestPersistenceAdapter(repository, ZoneId.of("Asia/Seoul"));
  }

  @Test
  void findsByTmpObjectKey() {
    VerificationRequest request =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    when(repository.findByTmpObjectKey("private/workout/a.jpg"))
        .thenReturn(Optional.of(VerificationRequestEntity.from(request)));

    Optional<VerificationRequest> loaded = adapter.findByTmpObjectKey("private/workout/a.jpg");

    assertThat(loaded).isPresent();
    assertThat(loaded.orElseThrow().getTmpObjectKey()).isEqualTo("private/workout/a.jpg");
  }

  @Test
  void savesDomain() {
    VerificationRequest request =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png");
    when(repository.save(any())).thenReturn(VerificationRequestEntity.from(request));

    VerificationRequest saved = adapter.save(request);

    assertThat(saved.getVerificationKind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
  }

  @Test
  void findsByVerificationIdForUpdate() {
    VerificationRequest request =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    when(repository.findByVerificationIdForUpdate(request.getVerificationId()))
        .thenReturn(Optional.of(VerificationRequestEntity.from(request)));

    Optional<VerificationRequest> loaded =
        adapter.findByVerificationIdForUpdate(request.getVerificationId());

    assertThat(loaded).isPresent();
    assertThat(loaded.orElseThrow().getVerificationId()).isEqualTo(request.getVerificationId());
  }

  @Test
  void findLatestUpdatedTodayUsesKstDayWindow() {
    LocalDate today = LocalDate.of(2026, 3, 13);
    when(repository
            .findFirstByUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
                eq(1L),
                eq(Instant.parse("2026-03-12T15:00:00Z")),
                eq(Instant.parse("2026-03-13T15:00:00Z"))))
        .thenReturn(Optional.empty());

    Optional<VerificationRequest> loaded = adapter.findLatestUpdatedToday(1L, today);

    assertThat(loaded).isEmpty();
    verify(repository)
        .findFirstByUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
            1L, Instant.parse("2026-03-12T15:00:00Z"), Instant.parse("2026-03-13T15:00:00Z"));
  }

  @Test
  void saveReturnsRefreshedUpdatedAtFromRepository() {
    VerificationRequest request =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png");
    Instant refreshed = Instant.parse("2026-03-13T01:02:03Z");
    VerificationRequestEntity savedEntity =
        VerificationRequestEntity.builder()
            .id(10L)
            .verificationId(request.getVerificationId())
            .userId(request.getUserId())
            .verificationKind(request.getVerificationKind().name())
            .status(request.getStatus().name())
            .tmpObjectKey(request.getTmpObjectKey())
            .createdAt(Instant.parse("2026-03-13T01:00:00Z"))
            .updatedAt(refreshed)
            .build();
    when(repository.save(any())).thenReturn(savedEntity);

    VerificationRequest saved = adapter.save(request);

    assertThat(saved.getUpdatedAt()).isEqualTo(refreshed);
  }
}
