package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(VerificationRequestPersistenceAdapter.class)
class VerificationRequestPersistenceAdapterIntegrationTest {

  @Autowired private VerificationRequestPersistenceAdapter adapter;

  @Autowired private UserJpaRepository userJpaRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void findsTodayActiveOrVerifiedWithinRequestedKstSlot() {
    Long userId = persistUser().getId();
    adapter.save(newRequest(userId, "older", VerificationStatus.REJECTED, "fp-1", daysAgo(2)));
    adapter.save(newRequest(userId, "today-rejected", VerificationStatus.REJECTED, "fp-2", now()));
    adapter.save(
        newRequest(userId, "today-pending", VerificationStatus.PENDING, "fp-3", nowPlusMinutes(5)));

    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.findTodayActiveOrVerified(userId, LocalDate.of(2026, 3, 8)))
        .map(VerificationRequest::getVerificationId)
        .contains("today-pending");
  }

  @Test
  void checksFingerprintByUserAndVerificationKind() {
    Long userId = persistUser().getId();
    adapter.save(newRequest(userId, "photo-a", VerificationStatus.REJECTED, "same-fp", now()));
    adapter.save(
        newRequest(userId, "record-a", VerificationStatus.REJECTED, "same-fp", nowPlusMinutes(1))
            .toBuilder()
            .verificationKind(VerificationKind.WORKOUT_RECORD)
            .build());

    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.existsSameFingerprint(userId, VerificationKind.WORKOUT_PHOTO, "same-fp"))
        .isTrue();
    assertThat(adapter.existsSameFingerprint(userId, VerificationKind.WORKOUT_RECORD, "same-fp"))
        .isTrue();
    assertThat(adapter.existsSameFingerprint(userId + 1, VerificationKind.WORKOUT_PHOTO, "same-fp"))
        .isFalse();
  }

  @Test
  void loadsLatestRequestByCreatedAtDescending() {
    Long userId = persistUser().getId();
    adapter.save(newRequest(userId, "first", VerificationStatus.REJECTED, "fp-1", daysAgo(1)));
    adapter.save(newRequest(userId, "second", VerificationStatus.PENDING, "fp-2", now()));

    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.findLatestByUserId(userId))
        .map(VerificationRequest::getVerificationId)
        .contains("second");
  }

  private UserEntity persistUser() {
    return userJpaRepository.saveAndFlush(
        UserEntity.builder()
            .email("user-" + System.nanoTime() + "@example.com")
            .provider(AuthProvider.LOCAL)
            .providerUserId("provider-" + System.nanoTime())
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .nickname("tester")
            .passwordHash("encoded-password")
            .build());
  }

  private VerificationRequest newRequest(
      Long userId,
      String verificationId,
      VerificationStatus status,
      String fingerprint,
      LocalDateTime createdAt) {
    return VerificationRequest.builder()
        .userId(userId)
        .verificationId(verificationId)
        .verificationKind(VerificationKind.WORKOUT_PHOTO)
        .status(status)
        .imageContentType("image/jpeg")
        .imageSizeBytes(1024L)
        .imageSha256("sha-" + verificationId)
        .requestFingerprint(fingerprint)
        .confidenceScore(BigDecimal.valueOf(0.8))
        .retryCount(0)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private LocalDateTime now() {
    return LocalDateTime.of(2026, 3, 8, 10, 0);
  }

  private LocalDateTime nowPlusMinutes(int minutes) {
    return now().plusMinutes(minutes);
  }

  private LocalDateTime daysAgo(int days) {
    return now().minusDays(days);
  }
}
