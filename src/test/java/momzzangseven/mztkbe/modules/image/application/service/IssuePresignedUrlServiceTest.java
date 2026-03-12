package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("IssuePresignedUrlService 단위 테스트")
class IssuePresignedUrlServiceTest {

  private static final String FAKE_PRESIGNED_URL = "https://s3.presigned.url/fake";

  @Mock private GeneratePresignedUrlPort generatePresignedUrlPort;

  @Mock private SaveImagePort saveImagePort;

  @InjectMocks private IssuePresignedUrlService service;

  @Nested
  @DisplayName("[D-5] MARKET 확장 로직 — n+1 구조 검증")
  class MarketExpansionTests {

    @Test
    @DisplayName("MARKET 1장 입력 → items 2개(THUMB+DETAIL) 생성")
    void execute_market1Image_produces2Items() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("MARKET 3장 입력 → items 4개(n+1) 생성")
    void execute_market3Images_produces4Items() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET, List.of("main.jpg", "d1.png", "d2.heic")));

      assertThat(result.items()).hasSize(4);
    }

    @Test
    @DisplayName("MARKET 5장 입력 → items 6개(n+1) 생성")
    void execute_market5Images_produces6Items() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L,
                  ImageReferenceType.MARKET,
                  List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg")));

      assertThat(result.items()).hasSize(6);
    }

    @Test
    @DisplayName("MARKET 첫 번째 파일 → items[0]은 THUMB prefix, items[1]은 DETAIL prefix")
    void execute_market_firstFileGeneratesThumbAndDetail() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      assertThat(result.items().get(0).tmpObjectKey()).startsWith("public/market/thumb/tmp/");
      assertThat(result.items().get(1).tmpObjectKey()).startsWith("public/market/detail/tmp/");
    }

    @Test
    @DisplayName("MARKET 첫 번째 파일에서 생성된 THUMB key와 DETAIL key는 서로 다른 UUID를 가진다")
    void execute_market_thumbAndDetailHaveDifferentUuids() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET, List.of("product.jpg")));

      String thumbKey = result.items().get(0).tmpObjectKey();
      String detailKey = result.items().get(1).tmpObjectKey();
      assertThat(thumbKey).isNotEqualTo(detailKey);
    }

    @Test
    @DisplayName("MARKET DB 저장 시 MARKET 타입은 없고 MARKET_THUMB/MARKET_DETAIL만 저장된다")
    @SuppressWarnings("unchecked")
    void execute_market_savesOnlyThumbAndDetailReferenceTypes() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
    @DisplayName("MARKET 3장: items[2]은 d1.png 기반 DETAIL, items[3]은 d2.heic 기반 DETAIL")
    void execute_market3Images_verifyDetailOrder() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET, List.of("main.jpg", "d1.png", "d2.heic")));

      assertThat(result.items().get(2).tmpObjectKey())
          .startsWith("public/market/detail/tmp/")
          .endsWith(".png");
      assertThat(result.items().get(3).tmpObjectKey())
          .startsWith("public/market/detail/tmp/")
          .endsWith(".heic");
    }
  }

  @Nested
  @DisplayName("[D-6] imgOrder 순서 보장")
  class ImgOrderTests {

    @Test
    @DisplayName("COMMUNITY_FREE 3장 요청 시 img_order = 1, 2, 3 (gap 없이 연속)")
    @SuppressWarnings("unchecked")
    void execute_communityFree3Images_imgOrderIsSequential() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
  @DisplayName("[H-5] WORKOUT S3 경로 매핑")
  class WorkoutPathTests {

    @Test
    @DisplayName("WORKOUT 단일 이미지 → private/workout/{uuid}.jpg 경로 (tmp/ 없음)")
    void execute_workout_usesPrivatePathWithoutTmpSubfolder() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.WORKOUT, List.of("exercise.jpg")));

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().get(0).tmpObjectKey()).startsWith("private/workout/");
      assertThat(result.items().get(0).tmpObjectKey()).doesNotContain("tmp/");
    }
  }

  @Nested
  @DisplayName("[H-9] Content-Type 매핑 검증")
  class ContentTypeTests {

    @Test
    @DisplayName("jpg 파일 → image/jpeg Content-Type으로 presigned URL 요청")
    void execute_jpg_usesImageJpegContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.jpg")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("jpeg 파일 → image/jpeg Content-Type으로 presigned URL 요청")
    void execute_jpeg_usesImageJpegContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.jpeg")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("png 파일 → image/png Content-Type으로 presigned URL 요청")
    void execute_png_usesImagePngContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.png")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("gif 파일 → image/gif Content-Type으로 presigned URL 요청")
    void execute_gif_usesImageGifContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.gif")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/gif");
    }

    @Test
    @DisplayName("heic 파일 → image/heic Content-Type으로 presigned URL 요청")
    void execute_heic_usesImageHeicContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.heic")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/heic");
    }

    @Test
    @DisplayName("heif 파일 → image/heif Content-Type으로 presigned URL 요청")
    void execute_heif_usesImageHeifContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.heif")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/heif");
    }

    @Test
    @DisplayName("대문자 확장자(.JPG) → image/jpeg Content-Type으로 presigned URL 요청")
    void execute_upperCaseJpg_usesImageJpegContentType() {
      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      given(
              generatePresignedUrlPort.generatePutPresignedUrl(
                  anyString(), contentTypeCaptor.capture()))
          .willReturn(FAKE_PRESIGNED_URL);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("FILE.JPG")));

      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/jpeg");
    }
  }

  @Nested
  @DisplayName("[H-1] PENDING Image 생성 검증")
  class PendingImageCreationTests {

    @Test
    @DisplayName("생성된 Image는 status=PENDING, referenceId=null, userId 일치")
    @SuppressWarnings("unchecked")
    void execute_createdImages_havePendingStatusAndNullReferenceId() {
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
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
      given(generatePresignedUrlPort.generatePutPresignedUrl(anyString(), anyString()))
          .willReturn(FAKE_PRESIGNED_URL);
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.COMMUNITY_FREE, List.of("photo.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      assertThat(result.items().get(0).tmpObjectKey())
          .isEqualTo(captor.getValue().get(0).getTmpObjectKey());
    }
  }
}
