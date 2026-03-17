package momzzangseven.mztkbe.modules.image.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.SignedRecoveryWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionIssuerWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionReceiptWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ImageInternalController 계약 테스트 (MockMVC + H2).
 *
 * <p>HandleLambdaCallbackUseCase는 @MockBean으로 처리하여 Controller 레이어 계약만 검증한다:
 *
 * <ul>
 *   <li>Webhook Secret 인증 (올바른/틀린/누락/빈 값)
 *   <li>Bean Validation (@NotNull, @NotBlank) 작동 여부
 *   <li>정상 응답 JSON 구조
 *   <li>UseCase 예외 → HTTP 상태코드 매핑
 * </ul>
 *
 * <p>테스트용 시크릿: "test-lambda-webhook-secret" (src/test/resources/application.yml 참조)
 */
@DisplayName("ImageInternalController 계약 테스트 (MockMVC + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageInternalControllerTest {

  private static final String URL = "/internal/images/lambda-callback";
  private static final String HEADER = "X-Lambda-Webhook-Secret";
  private static final String CORRECT_SECRET = "test-lambda-webhook-secret";
  private static final String WRONG_SECRET = "wrong-secret";

  @Autowired private MockMvc mockMvc;

  @MockBean private HandleLambdaCallbackUseCase handleLambdaCallbackUseCase;

  // web3 인프라 Bean 누락 방지 (IssuePresignedUrlControllerTest와 동일한 패턴)
  @MockBean private MarkTransactionSucceededUseCase txMarkSucceededUseCase;
  @MockBean private TransactionReceiptWorker txReceiptWorker;
  @MockBean private TransactionIssuerWorker txIssuerWorker;
  @MockBean private SignedRecoveryWorker txSignedRecoveryWorker;

  private static String completedBody() {
    return """
        {"status":"COMPLETED",
         "tmpObjectKey":"public/community/free/tmp/uuid.jpg",
         "finalObjectKey":"public/community/free/uuid.webp"}
        """;
  }

  private static String failedBody() {
    return """
        {"status":"FAILED",
         "tmpObjectKey":"public/community/free/tmp/uuid.jpg",
         "errorReason":"OOM error"}
        """;
  }

  // ========== 보안 — Secret 검증 ==========

  @Nested
  @DisplayName("보안 케이스 — X-Lambda-Webhook-Secret 검증")
  class SecretValidationTests {

    @Test
    @DisplayName("[SEC-1] 올바른 secret → 200 OK")
    void callback_correctSecret_returns200() throws Exception {
      willDoNothing().given(handleLambdaCallbackUseCase).execute(any());

      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(completedBody()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[SEC-2] 틀린 secret → 401 Unauthorized")
    void callback_wrongSecret_returns401() throws Exception {
      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, WRONG_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(completedBody()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[SEC-3] X-Lambda-Webhook-Secret 헤더 자체 누락 → 400 Bad Request")
    void callback_missingSecretHeader_returns400() throws Exception {
      mockMvc
          .perform(post(URL).contentType(APPLICATION_JSON).content(completedBody()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[SEC-4] secret=빈 문자열 → 401 Unauthorized")
    void callback_emptySecret_returns401() throws Exception {
      mockMvc
          .perform(
              post(URL).header(HEADER, "").contentType(APPLICATION_JSON).content(completedBody()))
          .andExpect(status().isUnauthorized());
    }
  }

  // ========== Bean Validation — Request Body ==========

  @Nested
  @DisplayName("Bean Validation — 요청 바디 검증")
  class RequestBodyValidationTests {

    @Test
    @DisplayName("[V-1] status 필드 누락 → 400 Bad Request")
    void callback_missingStatus_returns400() throws Exception {
      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"tmpObjectKey":"public/community/free/tmp/uuid.jpg",
                       "finalObjectKey":"public/community/free/uuid.webp"}
                      """))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[V-2] status=존재하지 않는 enum 값 → 400 Bad Request")
    void callback_invalidStatusEnum_returns400() throws Exception {
      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"status":"PROCESSING",
                       "tmpObjectKey":"public/community/free/tmp/uuid.jpg",
                       "finalObjectKey":"public/community/free/uuid.webp"}
                      """))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[V-3] tmpObjectKey 필드 누락 → 400 Bad Request")
    void callback_missingTmpObjectKey_returns400() throws Exception {
      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"status":"COMPLETED",
                       "finalObjectKey":"public/community/free/uuid.webp"}
                      """))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[V-4] tmpObjectKey=빈 문자열 → 400 Bad Request")
    void callback_blankTmpObjectKey_returns400() throws Exception {
      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"status":"COMPLETED",
                       "tmpObjectKey":"",
                       "finalObjectKey":"public/community/free/uuid.webp"}
                      """))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[V-5] FAILED + finalObjectKey, errorReason 모두 null → 200 (선택 필드)")
    void callback_failed_withBothOptionalFieldsNull_returns200() throws Exception {
      willDoNothing().given(handleLambdaCallbackUseCase).execute(any());

      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"status":"FAILED",
                       "tmpObjectKey":"public/community/free/tmp/uuid.jpg"}
                      """))
          .andExpect(status().isOk());
    }
  }

  // ========== Happy Day — 응답 구조 ==========

  @Nested
  @DisplayName("Happy Day — 정상 처리 응답 구조 검증")
  class HappyDayResponseTests {

    @Test
    @DisplayName("[H-1] COMPLETED 정상 콜백 → 200 + {status:SUCCESS, data:null}")
    void callback_completed_returns200WithSuccessBody() throws Exception {
      willDoNothing().given(handleLambdaCallbackUseCase).execute(any());

      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(completedBody()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("[H-2] FAILED 정상 콜백 → 200 + {status:SUCCESS, data:null}")
    void callback_failed_returns200WithSuccessBody() throws Exception {
      willDoNothing().given(handleLambdaCallbackUseCase).execute(any());

      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(failedBody()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
  }

  // ========== 에러 케이스 — UseCase 예외 전파 ==========

  @Nested
  @DisplayName("에러 케이스 — UseCase 예외의 HTTP 상태코드 매핑")
  class UseCaseExceptionTests {

    @Test
    @DisplayName("[E-1] ImageNotFoundException 발생 → 404 Not Found")
    void callback_imageNotFound_returns404() throws Exception {
      willThrow(new ImageNotFoundException("public/community/free/tmp/uuid.jpg"))
          .given(handleLambdaCallbackUseCase)
          .execute(any());

      mockMvc
          .perform(
              post(URL)
                  .header(HEADER, CORRECT_SECRET)
                  .contentType(APPLICATION_JSON)
                  .content(completedBody()))
          .andExpect(status().isNotFound());
    }
  }
}
