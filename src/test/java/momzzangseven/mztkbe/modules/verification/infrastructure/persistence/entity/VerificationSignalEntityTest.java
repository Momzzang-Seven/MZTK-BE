package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationSignal;
import momzzangseven.mztkbe.modules.verification.domain.vo.SignalType;
import org.junit.jupiter.api.Test;

class VerificationSignalEntityTest {

  @Test
  void fromMapsDomainFieldsIntoEntity() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 10, 0);
    VerificationSignal domain =
        VerificationSignal.builder()
            .id(5L)
            .verificationRequestId(11L)
            .signalType(SignalType.AI_RESULT)
            .signalKey("exercise_date")
            .signalValue("2026-03-08")
            .confidence(BigDecimal.valueOf(0.87))
            .createdAt(createdAt)
            .build();

    VerificationSignalEntity entity = VerificationSignalEntity.from(domain);

    assertThat(entity.getId()).isEqualTo(5L);
    assertThat(entity.getVerificationRequestId()).isEqualTo(11L);
    assertThat(entity.getSignalType()).isEqualTo(SignalType.AI_RESULT);
    assertThat(entity.getSignalKey()).isEqualTo("exercise_date");
    assertThat(entity.getSignalValue()).isEqualTo("2026-03-08");
    assertThat(entity.getConfidence()).isEqualByComparingTo("0.87");
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void toDomainMapsEntityFieldsBackIntoDomain() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 10, 0);
    VerificationSignalEntity entity =
        VerificationSignalEntity.builder()
            .id(5L)
            .verificationRequestId(11L)
            .signalType(SignalType.AI_RESULT)
            .signalKey("exercise_date")
            .signalValue("2026-03-08")
            .confidence(BigDecimal.valueOf(0.87))
            .createdAt(createdAt)
            .build();

    VerificationSignal domain = entity.toDomain();

    assertThat(domain.getId()).isEqualTo(5L);
    assertThat(domain.getVerificationRequestId()).isEqualTo(11L);
    assertThat(domain.getSignalType()).isEqualTo(SignalType.AI_RESULT);
    assertThat(domain.getSignalKey()).isEqualTo("exercise_date");
    assertThat(domain.getSignalValue()).isEqualTo("2026-03-08");
    assertThat(domain.getConfidence()).isEqualByComparingTo("0.87");
    assertThat(domain.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void onCreateAssignsCreatedAtWhenMissing() {
    VerificationSignalEntity entity =
        VerificationSignalEntity.builder()
            .verificationRequestId(11L)
            .signalType(SignalType.AI_RESULT)
            .signalKey("model_name")
            .signalValue("gpt-vision")
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  void querydslTypeExposesExpectedMetadata() {
    QVerificationSignalEntity root = QVerificationSignalEntity.verificationSignalEntity;
    QVerificationSignalEntity fromPath = new QVerificationSignalEntity(root);
    QVerificationSignalEntity fromMetadata = new QVerificationSignalEntity(forVariable("custom"));

    assertThat(root.verificationRequestId.getMetadata().getName())
        .isEqualTo("verificationRequestId");
    assertThat(root.signalKey.getMetadata().getName()).isEqualTo("signalKey");
    assertThat(fromPath.getType()).isEqualTo(VerificationSignalEntity.class);
    assertThat(fromMetadata.getMetadata().getName()).isEqualTo("custom");
  }
}
