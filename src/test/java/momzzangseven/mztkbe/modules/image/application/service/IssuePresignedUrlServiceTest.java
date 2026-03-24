package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
 *   <li>MARKET_CLASS 확장 로직 — 포트에 MARKET_CLASS_THUMB/MARKET_CLASS_DETAIL이 올바른 순서로 전달되는지
 *   <li>MARKET_STORE 확장 로직 — 포트에 MARKET_STORE_THUMB/MARKET_STORE_DETAIL이 올바른 순서로 전달되는지
 *   <li>각 referenceType별 올바른 extension이 포트에 전달되는지
 *   <li>imgOrder가 스펙 순서대로 부여되는지
 *   <li>PENDING Image 생성 시 필드 값(status, userId, tmpObjectKey)이 올바른지
 *   <li>응답 items에 imageId, presignedUrl, tmpObjectKey가 모두 포함되는지
 * </ul>
 *
 * <p>Content-Type 매핑은 어댑터 관심사이므로 S3PresignedUrlAdapterTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IssuePresignedUrlService 단위 테스트")
class IssuePresignedUrlServiceTest {

  private static final String FAKE_PRESIGNED_URL = "https://s3.presigned.url/fake";
  private static final String FAKE_OBJECT_KEY_PREFIX = "fake/key/uuid-";

  @Mock private GeneratePresignedUrlPort generatePresignedUrlPort;

  @Mock private SaveImagePort saveImagePort;

  @InjectMocks private IssuePresignedUrlService service;

  /**
   * 기본 stub 설정 헬퍼.
   *
   * <p>generatePresignedUrlPort: 호출마다 고유한 tmpObjectKey를 반환해 assembleItems()의 Collectors.toMap() 중복키
   * 예외를 방지한다.
   *
   * <p>saveImagePort.saveAll: 전달받은 Image 목록에 순번 id(1L, 2L, ...)를 부여하여 반환한다.
   */
  private void stubPort() {
    AtomicInteger keyCounter = new AtomicInteger(0);
    given(
            generatePresignedUrlPort.generatePutPresignedUrl(
                any(ImageReferenceType.class), anyString(), anyString()))
        .willAnswer(
            inv ->
                new PresignedUrlWithKey(
                    FAKE_PRESIGNED_URL,
                    FAKE_OBJECT_KEY_PREFIX + keyCounter.getAndIncrement() + ".jpg"));

    given(saveImagePort.saveAll(any()))
        .willAnswer(
            inv -> {
              List<Image> images = inv.getArgument(0);
              AtomicLong idSeq = new AtomicLong(1L);
              return images.stream()
                  .map(img -> img.toBuilder().id(idSeq.getAndIncrement()).build())
                  .toList();
            });
  }

  @Nested
  @DisplayName("[D-5] MARKET_CLASS 확장 로직 — n+1 구조 검증")
  class MarketClassExpansionTests {

    @Test
    @DisplayName("MARKET_CLASS 1장 입력 → items 2개(CLASS_THUMB+CLASS_DETAIL) 생성")
    void execute_marketClass1Image_produces2Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("MARKET_CLASS 3장 입력 → items 4개(n+1) 생성")
    void execute_marketClass3Images_produces4Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET_CLASS, List.of("main.jpg", "d1.png", "d2.heic")));

      assertThat(result.items()).hasSize(4);
    }

    @Test
    @DisplayName("MARKET_CLASS 5장 입력 → items 6개(n+1) 생성")
    void execute_marketClass5Images_produces6Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L,
                  ImageReferenceType.MARKET_CLASS,
                  List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg")));

      assertThat(result.items()).hasSize(6);
    }

    @Test
    @DisplayName("MARKET_CLASS 첫 번째 파일 → 포트에 MARKET_CLASS_THUMB, MARKET_CLASS_DETAIL 순으로 전달된다")
    void execute_marketClass_firstFilePassesThumbThenDetailToPort() {
      stubPort();
      ArgumentCaptor<ImageReferenceType> refTypeCaptor =
          ArgumentCaptor.forClass(ImageReferenceType.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      verify(generatePresignedUrlPort, times(2))
          .generatePutPresignedUrl(refTypeCaptor.capture(), anyString(), anyString());
      assertThat(refTypeCaptor.getAllValues().get(0))
          .isEqualTo(ImageReferenceType.MARKET_CLASS_THUMB);
      assertThat(refTypeCaptor.getAllValues().get(1))
          .isEqualTo(ImageReferenceType.MARKET_CLASS_DETAIL);
    }

    @Test
    @DisplayName("MARKET_CLASS 첫 번째 파일에서 THUMB와 DETAIL에 전달되는 uuid는 서로 다르다")
    void execute_marketClass_thumbAndDetailHaveDifferentUuids() {
      stubPort();
      ArgumentCaptor<String> uuidCaptor = ArgumentCaptor.forClass(String.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      verify(generatePresignedUrlPort, times(2))
          .generatePutPresignedUrl(
              any(ImageReferenceType.class), uuidCaptor.capture(), anyString());
      assertThat(uuidCaptor.getAllValues().get(0)).isNotEqualTo(uuidCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("MARKET_CLASS DB 저장 시 MARKET_CLASS 타입은 없고 CLASS_THUMB/CLASS_DETAIL만 저장된다")
    @SuppressWarnings("unchecked")
    void execute_marketClass_savesOnlyClassThumbAndClassDetailReferenceTypes() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> savedImages = captor.getValue();
      assertThat(savedImages)
          .noneMatch(img -> img.getReferenceType() == ImageReferenceType.MARKET_CLASS);
      assertThat(savedImages.get(0).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_CLASS_THUMB);
      assertThat(savedImages.get(1).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_CLASS_DETAIL);
    }

    @Test
    @DisplayName(
        "MARKET_CLASS 3장: 두 번째 파일(d1.png)은 extension=png, 세 번째 파일(d2.heic)은 extension=heic으로 포트에 전달된다")
    void execute_marketClass3Images_verifyExtensionOrder() {
      stubPort();
      ArgumentCaptor<String> extCaptor = ArgumentCaptor.forClass(String.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("main.jpg", "d1.png", "d2.heic")));

      // 포트 호출 순서: [0]=jpg(CLASS_THUMB), [1]=jpg(CLASS_DETAIL), [2]=png(CLASS_DETAIL),
      // [3]=heic(CLASS_DETAIL)
      verify(generatePresignedUrlPort, times(4))
          .generatePutPresignedUrl(any(ImageReferenceType.class), anyString(), extCaptor.capture());
      assertThat(extCaptor.getAllValues().get(2)).isEqualTo("png");
      assertThat(extCaptor.getAllValues().get(3)).isEqualTo("heic");
    }
  }

  @Nested
  @DisplayName("[D-5b] MARKET_STORE 확장 로직 — n+1 구조 검증")
  class MarketStoreExpansionTests {

    @Test
    @DisplayName("MARKET_STORE 1장 입력 → items 2개(STORE_THUMB+STORE_DETAIL) 생성")
    void execute_marketStore1Image_produces2Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET_STORE, List.of("store.jpg")));

      assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("MARKET_STORE 3장 입력 → items 4개(n+1) 생성")
    void execute_marketStore3Images_produces4Items() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET_STORE, List.of("main.jpg", "d1.png", "d2.heic")));

      assertThat(result.items()).hasSize(4);
    }

    @Test
    @DisplayName("MARKET_STORE 첫 번째 파일 → 포트에 MARKET_STORE_THUMB, MARKET_STORE_DETAIL 순으로 전달된다")
    void execute_marketStore_firstFilePassesThumbThenDetailToPort() {
      stubPort();
      ArgumentCaptor<ImageReferenceType> refTypeCaptor =
          ArgumentCaptor.forClass(ImageReferenceType.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_STORE, List.of("store.jpg")));

      verify(generatePresignedUrlPort, times(2))
          .generatePutPresignedUrl(refTypeCaptor.capture(), anyString(), anyString());
      assertThat(refTypeCaptor.getAllValues().get(0))
          .isEqualTo(ImageReferenceType.MARKET_STORE_THUMB);
      assertThat(refTypeCaptor.getAllValues().get(1))
          .isEqualTo(ImageReferenceType.MARKET_STORE_DETAIL);
    }

    @Test
    @DisplayName("MARKET_STORE DB 저장 시 MARKET_STORE 타입은 없고 STORE_THUMB/STORE_DETAIL만 저장된다")
    @SuppressWarnings("unchecked")
    void execute_marketStore_savesOnlyStoreThumbAndStoreDetailReferenceTypes() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_STORE, List.of("store.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> savedImages = captor.getValue();
      assertThat(savedImages)
          .noneMatch(img -> img.getReferenceType() == ImageReferenceType.MARKET_STORE);
      assertThat(savedImages.get(0).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_STORE_THUMB);
      assertThat(savedImages.get(1).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_STORE_DETAIL);
    }

    @Test
    @DisplayName(
        "MARKET_STORE 3장: 두 번째 파일(d1.png)은 extension=png, 세 번째 파일(d2.heic)은 extension=heic으로 포트에 전달된다")
    void execute_marketStore3Images_verifyExtensionOrder() {
      stubPort();
      ArgumentCaptor<String> extCaptor = ArgumentCaptor.forClass(String.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_STORE, List.of("main.jpg", "d1.png", "d2.heic")));

      // 포트 호출 순서: [0]=jpg(STORE_THUMB), [1]=jpg(STORE_DETAIL), [2]=png(STORE_DETAIL),
      // [3]=heic(STORE_DETAIL)
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
    @DisplayName("MARKET_CLASS 3장 입력(스펙 4개) 시 img_order = 1, 2, 3, 4 (gap 없이 연속)")
    @SuppressWarnings("unchecked")
    void execute_marketClass3Images_imgOrderIsSequentialFor4Specs() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("main.jpg", "d1.png", "d2.heic")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images).hasSize(4);
      for (int i = 0; i < 4; i++) {
        assertThat(images.get(i).getImgOrder()).isEqualTo(i + 1);
      }
    }

    @Test
    @DisplayName("MARKET_CLASS 1장 — CLASS_THUMB(img_order=1), CLASS_DETAIL(img_order=2) 순서")
    @SuppressWarnings("unchecked")
    void execute_marketClass_thumbIsOrder1_firstDetailIsOrder2() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images.get(0).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_CLASS_THUMB);
      assertThat(images.get(0).getImgOrder()).isEqualTo(1);
      assertThat(images.get(1).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_CLASS_DETAIL);
      assertThat(images.get(1).getImgOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("MARKET_STORE 1장 — STORE_THUMB(img_order=1), STORE_DETAIL(img_order=2) 순서")
    @SuppressWarnings("unchecked")
    void execute_marketStore_thumbIsOrder1_firstDetailIsOrder2() {
      stubPort();
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);

      service.execute(
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_STORE, List.of("store.jpg")));

      verify(saveImagePort).saveAll(captor.capture());
      List<Image> images = captor.getValue();
      assertThat(images.get(0).getReferenceType()).isEqualTo(ImageReferenceType.MARKET_STORE_THUMB);
      assertThat(images.get(0).getImgOrder()).isEqualTo(1);
      assertThat(images.get(1).getReferenceType())
          .isEqualTo(ImageReferenceType.MARKET_STORE_DETAIL);
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
  @DisplayName("[H-5b] USER_PROFILE — 표준 타입 (확장 없음)")
  class UserProfilePortCallTests {

    @Test
    @DisplayName("USER_PROFILE 단일 이미지 → 포트에 USER_PROFILE 타입 전달, items 1개 반환")
    void execute_userProfile_passesUserProfileRefTypeToPort() {
      stubPort();
      ArgumentCaptor<ImageReferenceType> refTypeCaptor =
          ArgumentCaptor.forClass(ImageReferenceType.class);

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.USER_PROFILE, List.of("profile.jpg")));

      verify(generatePresignedUrlPort)
          .generatePutPresignedUrl(refTypeCaptor.capture(), anyString(), anyString());
      assertThat(result.items()).hasSize(1);
      assertThat(refTypeCaptor.getValue()).isEqualTo(ImageReferenceType.USER_PROFILE);
    }
  }

  @Nested
  @DisplayName("[H-1] PENDING Image 생성 및 응답 구조 검증")
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
      given(saveImagePort.saveAll(any()))
          .willAnswer(
              inv -> {
                List<Image> images = inv.getArgument(0);
                return images.stream().map(img -> img.toBuilder().id(1L).build()).toList();
              });
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

    @Test
    @DisplayName("응답 items의 imageId는 saveAll이 반환한 Image의 DB id와 일치한다")
    void execute_responseItems_haveImageIdFromSavedDbRecord() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.COMMUNITY_FREE, List.of("photo.jpg")));

      // stubPort의 saveAll stub은 id=1L부터 순차 부여
      assertThat(result.items().get(0).imageId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("응답 items 전체에 null이 아닌 imageId가 포함된다")
    void execute_allResponseItems_haveNonNullImageId() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.COMMUNITY_FREE, List.of("a.jpg", "b.png", "c.gif")));

      assertThat(result.items()).allSatisfy(item -> assertThat(item.imageId()).isNotNull());
    }

    @Test
    @DisplayName("MARKET_CLASS 확장 시 응답 items 모두에 고유한 imageId가 부여된다")
    void execute_marketClass_allItemsHaveDistinctImageIds() {
      stubPort();

      IssuePresignedUrlResult result =
          service.execute(
              new IssuePresignedUrlCommand(
                  1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg")));

      assertThat(result.items()).hasSize(2);
      assertThat(result.items()).allSatisfy(item -> assertThat(item.imageId()).isNotNull());
      assertThat(result.items().get(0).imageId()).isNotEqualTo(result.items().get(1).imageId());
    }
  }
}
