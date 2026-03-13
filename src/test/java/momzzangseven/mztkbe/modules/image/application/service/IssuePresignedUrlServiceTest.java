package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
import momzzangseven.mztkbe.modules.image.application.port.out.PresignedUrlWithKey;
import momzzangseven.mztkbe.modules.image.application.port.out.SaveImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * IssuePresignedUrlService 단위 테스트.
 *
 * <p>리팩터링 후 서비스는 (referenceType, uuid, extension)을 Port에 위임하고, 경로 조립과 Content-Type 결정은
 * S3PresignedUrlAdapter(인프라)가 담당한다.
 *
 * <p>따라서 이 테스트에서 검증하는 핵심:
 *
 * <ul>
 *   <li>MARKET 확장 로직 — 포트에 MARKET_THUMB/MARKET_DETAIL이 올바른 순서로 전달되는지
 *   <li>각 referenceType별 올바른 extension이 포트에 전달되는지
 *   <li>imgOrder가 스펙 순서대로 부여되는지
 *   <li>PENDING Image 생성 시 필드 값(status, userId, tmpObjectKey)이 올바른지
 * </ul>
 *
 * <p>Content-Type 매핑은 어댑터 관심사이므로 S3PresignedUrlAdapterTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IssuePresignedUrlService 단위 테스트")
class IssuePresignedUrlServiceTest {

  private static final String FAKE_PRESIGNED_URL = "https://s3.presigned.url/fake";
  private static final String FAKE_OBJECT_KEY = "fake/key/uuid.jpg";

  @Mock private GeneratePresignedUrlPort generatePresignedUrlPort;

  @Mock private SaveImagePort saveImagePort;

  @InjectMocks private IssuePresignedUrlService service;

  /** 기본 stub 설정 헬퍼 — 모든 호출에 대해 고정 PresignedUrlWithKey를 반환한다. */
  private void stubPort() {
    given(
            generatePresignedUrlPort.generatePutPresignedUrl(
                any(ImageReferenceType.class), anyString(), anyString()))
        .willReturn(new PresignedUrlWithKey(FAKE_PRESIGNED_URL, FAKE_OBJECT_KEY));
  }

  @Nested
  @DisplayName("[D-5] MARKET 확장 로직 — n+1 구조 검증")
  class MarketExpansionTests {

    @Test
    @DisplayName("MARKET 1장 입력 → items 2개(THUMB+DETAIL) 생성")
    void execute_market1Image_produces2Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("MARKET 3장 입력 → items 4개(n+1) 생성")
    void execute_market3Images_produces4Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET, List.of("main.jpg", "d1.png", "d2.heic")));

      assertThat(result.items()).hasSize(4);
    }

    @Test
    @DisplayName("MARKET 5장 입력 → items 6개(n+1) 생성")
    void execute_market5Images_produces6Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L,
                  ImageReferenceType.MARKET,
                  List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg")));

      assertThat(result.items()).hasSize(6);
    }

    @Test
    @DisplayName("MARKET 첫 번째 파일 → 포트에 MARKET_THUMB, MARKET_DETAIL 순으로 전달된다")
    void execute_market_firstFilePassesThumbThenDetailToPort() {
      stubPort();
      ArgumentCaptor<ImageReferenceType> refTypeCaptor =
          ArgumentCaptor.forClass(ImageReferenceType.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      verify(generatePresignedUrlPort, times(2))
          .generatePutPresignedUrl(refTypeCaptor.capture(), anyString(), anyString());
      assertThat(refTypeCaptor.getAllValues().get(0)).isEqualTo(ImageReferenceType.MARKET_THUMB);
      assertThat(refTypeCaptor.getAllValues().get(1)).isEqualTo(ImageReferenceType.MARKET_DETAIL);
    }

    @Test
    @DisplayName("MARKET 첫 번째 파일에서 THUMB와 DETAIL에 전달되는 uuid는 서로 다르다")
    void execute_market_thumbAndDetailHaveDifferentUuids() {
      stubPort();
      ArgumentCaptor<String> uuidCaptor = ArgumentCaptor.forClass(String.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      verify(generatePresignedUrlPort, times(2))
          .generatePutPresignedUrl(
              any(ImageReferenceType.class), uuidCaptor.capture(), anyString());
      assertThat(uuidCaptor.getAllValues().get(0)).isNotEqualTo(uuidCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("MARKET DB 저장 시 MARKET 타입은 없고 MARKET_THUMB/MARKET_DETAIL만 저장된다")
    @SuppressWarnings("unchecked")
    void execute_market_savesOnlyThumbAndDetailReferenceTypes() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> savedImages = captor.getValue();
      assertThat(savedImages).noneMatch(img -> img.getReferenceType() == ImageReferenceType.MARKET);
      assertThat(savedImages.get(0).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_THUMB);
      assertThat(savedImages.get(1).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_DETAIL);
    }

    @Test
    @DisplayName(
        "MARKET 3장: 두 번째 파일(d1.png)은 extension=png, 세 번째 파일(d2.heic)은 extension=heic으로 포트에 전달된다")
    void execute_market3Images_verifyExtensionOrder() {
      stubPort();
      ArgumentCaptor<String> extCaptor = ArgumentCaptor.forClass(String.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET, List.of("main.jpg", "d1.png", "d2.heic")));

      // 포트 호출 순서: [0]=jpg(THUMB), [1]=jpg(DETAIL), [2]=png(DETAIL), [3]=heic(DETAIL)
      verify(generatePresignedUrlPort, times(4))
          .generatePutPresignedUrl(any(ImageReferenceType.class), anyString(), extCaptor.capture());
      assertThat(extCaptor.getAllValues().get(2)).isEqualTo("png");
      assertThat(extCaptor.getAllValues().get(3)).isEqualTo("heic");
    }
  }

  @Nested
  @DisplayName("[D-6] imgOrder 순서 보장")
  class ImgOrderTests {

    @Test
    @DisplayName("COMMUNITY_FREE 3장 요청 시 img_order = 1, 2, 3 (gap 없이 연속)")
    @SuppressWarnings("unchecked")
    void execute_communityFree3Images_imgOrderIsSequential() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("a.jpg", "b.jpg", "c.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images.get(0).getImgOrder()).isEqualTo(1);
      assertThat(images.get(1).getImgOrder()).isEqualTo(2);
      assertThat(images.get(2).getImgOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("MARKET 3장 입력(스펙 4개) 시 img_order = 1, 2, 3, 4 (gap 없이 연속)")
    @SuppressWarnings("unchecked")
    void execute_market3Images_imgOrderIsSequentialFor4Specs() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET, List.of("main.jpg", "d1.png", "d2.heic")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images).hasSize(4);
      for (int i = 0; i < 4; i++) {
        assertThat(images.get(i).getImgOrder()).isEqualTo(i + 1);
      }
    }

    @Test
    @DisplayName("MARKET 1장 — THUMB(img_order=1), DETAIL(img_order=2) 순서")
    @SuppressWarnings("unchecked")
    void execute_market_thumbIsOrder1_firstDetailIsOrder2() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images.get(0).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_THUMB);
      assertThat(images.get(0).getImgOrder()).isEqualTo(1);
      assertThat(images.get(1).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_DETAIL);
      assertThat(images.get(1).getImgOrder()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("[H-5] WORKOUT — 포트에 올바른 referenceType과 extension이 전달되는지")
  class WorkoutPortCallTests {

    @Test
    @DisplayName("WORKOUT 단일 이미지 → 포트에 WORKOUT 타입, jpg 확장자 전달")
    void execute_workout_passesWorkoutRefTypeAndExtensionToPort() {
      stubPort();
      ArgumentCaptor<ImageReferenceType> refTypeCaptor =
          ArgumentCaptor.forClass(ImageReferenceType.class);
      ArgumentCaptor<String> extCaptor = ArgumentCaptor.forClass(String.class);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.WORKOUT, List.of("exercise.jpg")));

      verify(generatePresignedUrlPort)
          .generatePutPresignedUrl(refTypeCaptor.capture(), anyString(), extCaptor.capture());
      assertThat(result.items()).hasSize(1);
      assertThat(refTypeCaptor.getValue()).isEqualTo(ImageReferenceType.WORKOUT);
      assertThat(extCaptor.getValue()).isEqualTo("jpg");
    }
  }

  @Nested
  @DisplayName("[H-1] PENDING Image 생성 검증")
  class PendingImageCreationTests {

    @Test
    @DisplayName("생성된 Image는 status=PENDING, referenceId=null, userId 일치")
    @SuppressWarnings("unchecked")
    void execute_createdImages_havePendingStatusAndNullReferenceId() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              42L, ImageReferenceType.COMMUNITY_FREE, List.of("photo.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      Image image = captor.getValue().get(0);
      assertThat(image.getStatus()).isEqualTo(ImageStatus.PENDING);
      assertThat(image.getReferenceId()).isNull();
      assertThat(image.getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("응답 items와 DB 저장 개수가 일치한다")
    @SuppressWarnings("unchecked")
    void execute_responseItemCountMatchesSavedImageCount() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.COMMUNITY_FREE, List.of("a.jpg", "b.png")));

      verify(saveImagePort).saveAll(captor.capture());
      assertThat(result.items()).hasSize(captor.getValue().size());
    }

    @Test
    @DisplayName("응답 tmpObjectKey와 DB 저장 tmpObjectKey가 일치한다")
    @SuppressWarnings("unchecked")
    void execute_responseTmpObjectKeyMatchesSavedKey() {
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  any(ImageReferenceType.class), anyString(), anyString()))
          .willReturn(new PresignedUrlWithKey(FAKE_PRESIGNED_URL, "specific/key/photo.jpg"));
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.COMMUNITY_FREE, List.of("photo.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      assertThat(result.items().get(0).tmpObjectKey())
          .isEqualTo(captor.getValue().get(0).getTmpObjectKey())
          .isEqualTo("specific/key/photo.jpg");
    }
  }
}
