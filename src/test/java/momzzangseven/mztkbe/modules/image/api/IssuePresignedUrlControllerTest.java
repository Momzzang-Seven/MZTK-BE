package momzzangseven.mztkbe.modules.image.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.IntStream;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.dto.PresignedUrlItem;
import momzzangseven.mztkbe.modules.image.application.port.in.IssuePresignedUrlUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.SignedRecoveryWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionIssuerWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionReceiptWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ImageController 계약 테스트 (MockMVC + H2).
 *
 * <p>IssuePresignedUrlUseCase는 @MockBean으로 Mock 처리하여 Controller 레이어 계약만 검증한다:
 *
 * <ul>
 *   <li>Bean Validation (@NotNull, @NotEmpty, @NotBlank) 작동 여부
 *   <li>인증/인가 (401 미인증)
 *   <li>잘못된 enum 값 처리 (HttpMessageNotReadableException)
 *   <li>정상 응답 JSON 구조
 * </ul>
 */
@DisplayName("IssuePresignedUrl 컨트롤러 계약 테스트 (MockMVC + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssuePresignedUrlControllerTest {

  private static final String URL = "/images/presigned-urls";

  @Autowired private MockMvc mockMvc;

  @MockBean private IssuePresignedUrlUseCase issuePresignedUrlUseCase;

  // web3 인프라 Bean 일부가 조건부 활성화여서 @SpringBootTest 컨텍스트 로딩 시 누락 방지
  @MockBean private MarkTransactionSucceededUseCase txMarkSucceededUseCase;
  @MockBean private TransactionReceiptWorker txReceiptWorker;
  @MockBean private TransactionIssuerWorker txIssuerWorker;
  @MockBean private SignedRecoveryWorker txSignedRecoveryWorker;

  private static UsernamePasswordAuthenticationToken authAs(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  private static IssuePresignedUrlResult fakeResult(int count) {
    List<PresignedUrlItem> items =
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new PresignedUrlItem(
                        (long) i + 1,
                        "https://s3.presigned.url/fake-" + i,
                        "public/community/free/tmp/uuid-" + i + ".jpg"))
            .toList();
    return IssuePresignedUrlResult.of(items);
  }

  // ─── 인증/인가 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("[E-미인증] 인증 토큰 없이 요청 시 401 반환")
  void issuePresignedUrls_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE","images":["photo.jpg"]}
                    """))
        .andExpect(status().isUnauthorized());
  }

  // ─── Bean Validation ────────────────────────────────────────────────────────

  @Test
  @DisplayName("[E-1] referenceType 필드 누락 시 400 반환")
  void issuePresignedUrls_returns400_whenReferenceTypeMissing() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content("""
                    {"images":["photo.jpg"]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-2] referenceType이 정의되지 않은 값(INVALID_TYPE)이면 400 반환")
  void issuePresignedUrls_returns400_whenReferenceTypeIsInvalid() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"INVALID_TYPE","images":["photo.jpg"]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-5] images 필드 누락 시 400 반환")
  void issuePresignedUrls_returns400_whenImagesMissing() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-6] images 빈 배열 시 400 반환")
  void issuePresignedUrls_returns400_whenImagesIsEmpty() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE","images":[]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-7] images 원소가 빈 문자열이면 400 반환")
  void issuePresignedUrls_returns400_whenImageElementIsBlank() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE","images":[""]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[E-8] images 원소가 공백 문자열이면 400 반환")
  void issuePresignedUrls_returns400_whenImageElementIsWhitespace() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE","images":[" "]}
                    """))
        .andExpect(status().isBadRequest());
  }

  // ─── Happy Day ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("[H-1] COMMUNITY_FREE 단일 이미지 — 200 + items 크기 1 + 응답 구조 검증")
  void issuePresignedUrls_returns200_withCorrectResponseStructure() throws Exception {
    given(issuePresignedUrlUseCase.execute(any())).willReturn(fakeResult(1));

    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"COMMUNITY_FREE","images":["photo.jpg"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].imageId").isNumber())
        .andExpect(jsonPath("$.data.items[0].presignedUrl").isString())
        .andExpect(jsonPath("$.data.items[0].tmpObjectKey").isString());
  }

  @Test
  @DisplayName("[H-6] MARKET 단일 이미지 — use case가 2개 items 반환 시 응답에도 2개")
  void issuePresignedUrls_returns200_with2Items_forMarket() throws Exception {
    given(issuePresignedUrlUseCase.execute(any())).willReturn(fakeResult(2));

    mockMvc
        .perform(
            post(URL)
                .with(authentication(authAs(1L)))
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"referenceType":"MARKET","images":["product.jpg"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(2));
  }
}
