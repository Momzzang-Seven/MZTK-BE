package momzzangseven.mztkbe.modules.image.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByIdsUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.SignedRecoveryWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionIssuerWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionReceiptWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GetImagesByIds 컨트롤러 계약 테스트 (MockMVC + H2).
 *
 * <p>GetImagesByIdsUseCase는 @MockitoBean으로 처리하여 Controller 레이어 계약만 검증한다:
 *
 * <ul>
 *   <li>인증/인가 (401 미인증)
 *   <li>필수 파라미터 누락 시 400
 *   <li>정상 응답 JSON 구조
 * </ul>
 */
@DisplayName("GetImagesByIds 컨트롤러 계약 테스트 (MockMVC + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GetImagesByIdsControllerTest {

  private static final String URL = "/images";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetImagesByIdsUseCase getImagesByIdsUseCase;

  @MockitoBean private MarkTransactionSucceededUseCase txMarkSucceededUseCase;
  @MockitoBean private TransactionReceiptWorker txReceiptWorker;
  @MockitoBean private TransactionIssuerWorker txIssuerWorker;
  @MockitoBean private SignedRecoveryWorker txSignedRecoveryWorker;

  private static UsernamePasswordAuthenticationToken authAs(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  private static GetImagesByIdsResult fakeResult() {
    ImageItem item =
        new ImageItem(
            1L,
            1L,
            ImageReferenceType.COMMUNITY_FREE,
            100L,
            ImageStatus.COMPLETED,
            "public/community/free/abc123.webp",
            1,
            Instant.parse("2026-03-27T12:00:00Z"),
            Instant.parse("2026-03-27T12:01:00Z"));
    return new GetImagesByIdsResult(List.of(item));
  }

  // ─── 인증/인가 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("[E-미인증] 인증 토큰 없이 요청 시 401 반환")
  void getImagesByIds_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get(URL)
                .param("ids", "1")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isUnauthorized());
  }

  // ─── 파라미터 누락 400 ────────────────────────────────────────────────────

  @Test
  @DisplayName("[E-1] ids 파라미터 누락 시 400 반환")
  void getImagesByIds_returns400_whenIdsMissing() throws Exception {
    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-2] referenceType 파라미터 누락 시 400 반환")
  void getImagesByIds_returns400_whenReferenceTypeMissing() throws Exception {
    mockMvc
        .perform(
            get(URL).with(authentication(authAs(1L))).param("ids", "1").param("referenceId", "100"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-3] referenceId 파라미터 누락 시 400 반환")
  void getImagesByIds_returns400_whenReferenceIdMissing() throws Exception {
    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "COMMUNITY_FREE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-4] referenceType이 정의되지 않은 값이면 400 반환")
  void getImagesByIds_returns400_whenReferenceTypeIsInvalid() throws Exception {
    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "INVALID_TYPE")
                .param("referenceId", "100"))
        .andExpect(status().isBadRequest());
  }

  // ─── 예외 매핑 400/403 ─────────────────────────────────────────────────────

  @Test
  @DisplayName("[E-6] internal-only referenceType → InvalidImageRefTypeException → 400 IMAGE_006")
  void getImagesByIds_returns400_whenInternalOnlyReferenceType() throws Exception {
    given(getImagesByIdsUseCase.execute(any()))
        .willThrow(new InvalidImageRefTypeException("internal only type"));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "MARKET_STORE_THUMB")
                .param("referenceId", "100"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IMAGE_006"));
  }

  @Test
  @DisplayName("[E-10] 다른 사용자 이미지 — ImageNotBelongsToUserException → 403 IMAGE_009")
  void getImagesByIds_returns403_whenImageBelongsToAnotherUser() throws Exception {
    given(getImagesByIdsUseCase.execute(any()))
        .willThrow(new ImageNotBelongsToUserException("not your image"));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IMAGE_009"));
  }

  @Test
  @DisplayName("[E-11] referenceId 불일치 — ImageNotBelongsToUserException → 403 IMAGE_009")
  void getImagesByIds_returns403_whenReferenceIdMismatch() throws Exception {
    given(getImagesByIdsUseCase.execute(any()))
        .willThrow(new ImageNotBelongsToUserException("referenceId mismatch"));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "999"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IMAGE_009"));
  }

  @Test
  @DisplayName("[E-12] ids 개수 초과 — ImageMaxCountExceedException → 400 IMAGE_004")
  void getImagesByIds_returns400_whenIdsCountExceedsLimit() throws Exception {
    given(getImagesByIdsUseCase.execute(any()))
        .willThrow(new ImageMaxCountExceedException("count exceeded"));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IMAGE_004"));
  }

  // ─── Happy Day ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("[H-1] 정상 요청 — 200 + images 배열 + 응답 구조 검증")
  void getImagesByIds_returns200_withCorrectResponseStructure() throws Exception {
    given(getImagesByIdsUseCase.execute(any())).willReturn(fakeResult());

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.images").isArray())
        .andExpect(jsonPath("$.data.images.length()").value(1))
        .andExpect(jsonPath("$.data.images[0].imageId").isNumber())
        .andExpect(jsonPath("$.data.images[0].userId").isNumber())
        .andExpect(jsonPath("$.data.images[0].referenceType").isString())
        .andExpect(jsonPath("$.data.images[0].referenceId").isNumber())
        .andExpect(jsonPath("$.data.images[0].status").isString())
        .andExpect(jsonPath("$.data.images[0].imgOrder").isNumber())
        .andExpect(jsonPath("$.data.images[0].createdAt").isString())
        .andExpect(jsonPath("$.data.images[0].updatedAt").isString());
  }

  @Test
  @DisplayName("[H-2] 다수 ids 파라미터 — 200 + 복수 images 반환")
  void getImagesByIds_returns200_withMultipleIds() throws Exception {
    ImageItem item1 =
        new ImageItem(
            1L,
            1L,
            ImageReferenceType.COMMUNITY_FREE,
            100L,
            ImageStatus.COMPLETED,
            "key1.webp",
            1,
            Instant.now(),
            Instant.now());
    ImageItem item2 =
        new ImageItem(
            2L,
            1L,
            ImageReferenceType.COMMUNITY_FREE,
            100L,
            ImageStatus.PENDING,
            null,
            2,
            Instant.now(),
            Instant.now());
    given(getImagesByIdsUseCase.execute(any()))
        .willReturn(new GetImagesByIdsResult(List.of(item1, item2)));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1", "2")
                .param("referenceType", "COMMUNITY_FREE")
                .param("referenceId", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.images.length()").value(2));
  }
}
