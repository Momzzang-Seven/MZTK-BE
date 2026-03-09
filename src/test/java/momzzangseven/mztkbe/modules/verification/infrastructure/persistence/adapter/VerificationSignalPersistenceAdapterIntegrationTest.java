package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationSignal;
import momzzangseven.mztkbe.modules.verification.domain.vo.SignalType;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({VerificationRequestPersistenceAdapter.class, VerificationSignalPersistenceAdapter.class})
class VerificationSignalPersistenceAdapterIntegrationTest {

  @Autowired private VerificationRequestPersistenceAdapter requestAdapter;

  @Autowired private VerificationSignalPersistenceAdapter signalAdapter;

  @Autowired private UserJpaRepository userJpaRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void savesSignalsAndLoadsThemByRequestId() {
    Long requestId = persistRequest().getId();

    signalAdapter.save(
        VerificationSignal.builder()
            .verificationRequestId(requestId)
            .signalType(SignalType.AI_RESULT)
            .signalKey("photo_verdict")
            .signalValue("WORKOUT_PHOTO")
            .confidence(BigDecimal.valueOf(0.91))
            .build());

    entityManager.flush();
    entityManager.clear();

    assertThat(signalAdapter.findByVerificationRequestId(requestId))
        .extracting(VerificationSignal::getSignalKey)
        .containsExactly("photo_verdict");
  }

  @Test
  void duplicateSignalKeyPerRequestIsRejected() {
    Long requestId = persistRequest().getId();
    VerificationSignal signal = newSignal(requestId);

    signalAdapter.save(signal);

    assertThatThrownBy(() -> signalAdapter.save(signal))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private VerificationSignal newSignal(Long requestId) {
    return VerificationSignal.builder()
        .verificationRequestId(requestId)
        .signalType(SignalType.AI_RESULT)
        .signalKey("photo_verdict")
        .signalValue("WORKOUT_PHOTO")
        .confidence(BigDecimal.valueOf(0.91))
        .build();
  }

  private VerificationRequest persistRequest() {
    Long userId =
        userJpaRepository
            .saveAndFlush(
                UserEntity.builder()
                    .email("signal-" + System.nanoTime() + "@example.com")
                    .provider(AuthProvider.LOCAL)
                    .providerUserId("provider-" + System.nanoTime())
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .nickname("tester")
                    .passwordHash("encoded-password")
                    .build())
            .getId();

    return requestAdapter.save(
        VerificationRequest.builder()
            .userId(userId)
            .verificationId("verification-" + System.nanoTime())
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.PENDING)
            .imageContentType("image/jpeg")
            .imageSizeBytes(1024L)
            .imageSha256("sha-request")
            .requestFingerprint("fp-request")
            .confidenceScore(BigDecimal.valueOf(0.5))
            .retryCount(0)
            .createdAt(LocalDateTime.of(2026, 3, 8, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 3, 8, 10, 0))
            .build());
  }
}
