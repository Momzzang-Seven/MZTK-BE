package momzzangseven.mztkbe.modules.image.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.adaptor.ImagePersistenceAdapter;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImagePersistenceAdapter 단위 테스트")
class ImagePersistenceAdapterTest {

  @Mock private ImageJpaRepository imageJpaRepository;

  @InjectMocks private ImagePersistenceAdapter adapter;

  // ─────────────────────────────────────────────────────────────────────────
  // 픽스처 헬퍼
  // ─────────────────────────────────────────────────────────────────────────

  private static Image buildDomainImage(
      Long id, Long userId, ImageReferenceType refType, ImageStatus status) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(refType)
        .referenceId(null)
        .status(status)
        .tmpObjectKey("public/community/free/tmp/uuid.jpg")
        .finalObjectKey(null)
        .imgOrder(1)
        .createdAt(Instant.parse("2026-03-11T10:00:00Z"))
        .updatedAt(Instant.parse("2026-03-11T10:00:00Z"))
        .build();
  }

  private static ImageEntity buildEntity(
      Long id, Long userId, ImageReferenceType refType, ImageStatus status) {
    return ImageEntity.builder()
        .id(id)
        .userId(userId)
        .referenceType(refType.name())
        .referenceId(null)
        .status(status.name())
        .tmpObjectKey("public/community/free/tmp/uuid.jpg")
        .finalObjectKey(null)
        .imgOrder(1)
        .build();
  }

  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("saveAll() — 도메인↔엔티티 변환 및 JPA 위임")
  class SaveAllTests {

    @Test
    @DisplayName("domain → entity 변환 시 referenceType이 String(name())으로 저장된다")
    @SuppressWarnings("unchecked")
    void saveAll_convertsReferenceTypeToString() {
      Image domain =
          buildDomainImage(null, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      ImageEntity savedEntity =
          buildEntity(10L, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      ArgumentCaptor<List<ImageEntity>> captor = ArgumentCaptor.forClass(List.class);
      adapter.saveAll(List.of(domain));

      verify(imageJpaRepository).saveAll(captor.capture());
      ImageEntity entity = captor.getValue().get(0);
      assertThat(entity.getReferenceType()).isEqualTo("COMMUNITY_FREE");
    }

    @Test
    @DisplayName("domain → entity 변환 시 status가 String(name())으로 저장된다")
    @SuppressWarnings("unchecked")
    void saveAll_convertsStatusToString() {
      Image domain =
          buildDomainImage(null, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      ImageEntity savedEntity =
          buildEntity(10L, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      ArgumentCaptor<List<ImageEntity>> captor = ArgumentCaptor.forClass(List.class);
      adapter.saveAll(List.of(domain));

      verify(imageJpaRepository).saveAll(captor.capture());
      ImageEntity entity = captor.getValue().get(0);
      assertThat(entity.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("domain → entity 변환 시 나머지 필드(userId, tmpObjectKey, imgOrder)가 정확히 매핑된다")
    @SuppressWarnings("unchecked")
    void saveAll_mapsAllFieldsCorrectly() {
      Image domain = buildDomainImage(null, 42L, ImageReferenceType.WORKOUT, ImageStatus.PENDING);
      domain = domain.toBuilder().tmpObjectKey("private/workout/uuid.jpg").imgOrder(3).build();
      ImageEntity savedEntity =
          ImageEntity.builder()
              .id(99L)
              .userId(42L)
              .referenceType("WORKOUT")
              .status("PENDING")
              .tmpObjectKey("private/workout/uuid.jpg")
              .imgOrder(3)
              .build();
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      ArgumentCaptor<List<ImageEntity>> captor = ArgumentCaptor.forClass(List.class);
      adapter.saveAll(List.of(domain));

      verify(imageJpaRepository).saveAll(captor.capture());
      ImageEntity entity = captor.getValue().get(0);
      assertThat(entity.getUserId()).isEqualTo(42L);
      assertThat(entity.getTmpObjectKey()).isEqualTo("private/workout/uuid.jpg");
      assertThat(entity.getImgOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("entity → domain 역변환 시 referenceType이 enum으로 복원된다")
    @SuppressWarnings("unchecked")
    void saveAll_reverseConvertReferenceTypeToEnum() {
      Image domain =
          buildDomainImage(null, 1L, ImageReferenceType.MARKET_THUMB, ImageStatus.PENDING);
      ImageEntity savedEntity =
          buildEntity(10L, 1L, ImageReferenceType.MARKET_THUMB, ImageStatus.PENDING);
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      List<Image> result = adapter.saveAll(List.of(domain));

      assertThat(result.get(0).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_THUMB);
    }

    @Test
    @DisplayName("entity → domain 역변환 시 status가 enum으로 복원된다")
    @SuppressWarnings("unchecked")
    void saveAll_reverseConvertStatusToEnum() {
      Image domain =
          buildDomainImage(null, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      ImageEntity savedEntity =
          buildEntity(10L, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      List<Image> result = adapter.saveAll(List.of(domain));

      assertThat(result.get(0).getStatus()).isEqualTo(ImageStatus.PENDING);
    }

    @Test
    @DisplayName("entity → domain 역변환 시 DB 생성 id가 도메인 모델에 반영된다")
    @SuppressWarnings("unchecked")
    void saveAll_reverseConvertPersistenceIdToDomain() {
      Image domain =
          buildDomainImage(null, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      ImageEntity savedEntity =
          buildEntity(77L, 1L, ImageReferenceType.COMMUNITY_FREE, ImageStatus.PENDING);
      given(imageJpaRepository.saveAll(anyList())).willReturn(List.of(savedEntity));

      List<Image> result = adapter.saveAll(List.of(domain));

      assertThat(result.get(0).getId()).isEqualTo(77L);
    }
  }

  @Nested
  @DisplayName("deletePendingImagesBefore() — JPA 위임 및 반환값 전파")
  class DeleteTests {

    @Test
    @DisplayName("deletePendingImagesBefore() → imageJpaRepository.deletePendingBefore() 호출 확인")
    void deletePendingImagesBefore_delegatesToRepository() {
      Instant cutoff = Instant.parse("2026-03-11T05:00:00Z");
      given(imageJpaRepository.deletePendingBefore(cutoff, 100)).willReturn(5);

      int result = adapter.deletePendingImagesBefore(cutoff, 100);

      verify(imageJpaRepository).deletePendingBefore(cutoff, 100);
      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("삭제 대상 없을 때 0 반환값이 그대로 전파된다")
    void deletePendingImagesBefore_returnsZero_whenNothingDeleted() {
      Instant cutoff = Instant.now();
      given(imageJpaRepository.deletePendingBefore(any(Instant.class), anyInt())).willReturn(0);

      int result = adapter.deletePendingImagesBefore(cutoff, 100);

      assertThat(result).isZero();
    }
  }
}
