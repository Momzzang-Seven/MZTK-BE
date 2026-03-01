package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.GlobalExceptionHandler;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class TokenTransferControllerMockMvcTest {

  @Mock private PrepareTokenTransferUseCase prepareTokenTransferUseCase;

  @Mock private SubmitTokenTransferUseCase submitTokenTransferUseCase;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    TokenTransferController controller =
        new TokenTransferController(prepareTokenTransferUseCase, submitTokenTransferUseCase);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
            .build();
  }

  @Test
  void prepare_returnsCreated_whenRequestIsValid() throws Exception {
    when(prepareTokenTransferUseCase.execute(any(PrepareTokenTransferCommand.class)))
        .thenReturn(
            PrepareTokenTransferResult.builder()
                .prepareId("p-123")
                .idempotencyKey("domain:QUESTION_REWARD:77:1")
                .txType("EIP7702")
                .authorityAddress("0x1111111111111111111111111111111111111111")
                .authorityNonce(7L)
                .delegateTarget("0x2222222222222222222222222222222222222222")
                .authExpiresAt(LocalDateTime.now().plusMinutes(5))
                .payloadHashToSign(
                    "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .build());

    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "domainType": "QUESTION_REWARD",
                      "referenceId": "77",
                      "toUserId": 2,
                      "amountWei": "1000000000000000000"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.prepareId").value("p-123"))
        .andExpect(jsonPath("$.data.idempotencyKey").value("domain:QUESTION_REWARD:77:1"));

    verify(prepareTokenTransferUseCase).execute(any(PrepareTokenTransferCommand.class));
  }

  @Test
  void prepare_returnsBadRequest_whenDomainTypeIsInvalidEnum() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "domainType": "INVALID_DOMAIN",
                      "referenceId": "77",
                      "toUserId": 2,
                      "amountWei": "1000000000000000000"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  void prepare_returnsBadRequest_whenValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "domainType": "QUESTION_REWARD",
                      "referenceId": "77",
                      "amountWei": "1000000000000000000"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.message").value("Validation failed"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  void prepare_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "domainType": "QUESTION_REWARD",
                      "referenceId": "77",
                      "toUserId": 2,
                      "amountWei": "1000000000000000000"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  void submit_returnsAccepted_whenRequestIsValid() throws Exception {
    when(submitTokenTransferUseCase.execute(any(SubmitTokenTransferCommand.class)))
        .thenReturn(
            SubmitTokenTransferResult.builder()
                .transactionId(10L)
                .status("SIGNED")
                .txHash("0x" + "a".repeat(64))
                .build());

    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "prepareId": "123e4567-e89b-12d3-a456-426614174000",
                      "authorizationSignature": "%s",
                      "executionSignature": "%s"
                    }
                    """
                        .formatted(signature("b"), signature("c"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.transactionId").value(10))
        .andExpect(jsonPath("$.data.status").value("SIGNED"));

    verify(submitTokenTransferUseCase).execute(any(SubmitTokenTransferCommand.class));
  }

  @Test
  void submit_returnsBadRequest_whenValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "prepareId": "123e4567-e89b-12d3-a456-426614174000",
                      "authorizationSignature": "%s"
                    }
                    """
                        .formatted(signature("b"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.message").value("Validation failed"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  void submit_returnsBadRequest_whenPrepareIdIsInvalidFormat() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .principal(() -> "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "prepareId": "not-uuid",
                      "authorizationSignature": "%s",
                      "executionSignature": "%s"
                    }
                    """
                        .formatted(signature("b"), signature("c"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  void submit_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "prepareId": "123e4567-e89b-12d3-a456-426614174000",
                      "authorizationSignature": "%s",
                      "executionSignature": "%s"
                    }
                    """
                        .formatted(signature("b"), signature("c"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  private String signature(String hexChar) {
    return "0x" + hexChar.repeat(130);
  }

  private static class TestAuthenticationPrincipalResolver
      implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
          && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory) {
      if (webRequest.getUserPrincipal() == null) {
        return null;
      }
      return Long.parseLong(webRequest.getUserPrincipal().getName());
    }
  }
}
