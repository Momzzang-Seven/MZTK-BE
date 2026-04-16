package momzzangseven.mztkbe.modules.image.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.ImageLookupItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.LookupStatus;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesStatusUseCase;
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

@DisplayName("GetImagesStatus controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GetImagesStatusControllerTest {

  private static final String URL = "/images/status";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetImagesStatusUseCase getImagesStatusUseCase;

  @MockitoBean private MarkTransactionSucceededUseCase txMarkSucceededUseCase;
  @MockitoBean private TransactionReceiptWorker txReceiptWorker;
  @MockitoBean private TransactionIssuerWorker txIssuerWorker;
  @MockitoBean private SignedRecoveryWorker txSignedRecoveryWorker;

  private static UsernamePasswordAuthenticationToken authAs(Long userId) {
    return new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("unauthenticated request returns 401")
  void getImagesStatus_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get(URL).param("ids", "1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("missing ids returns 400 VALIDATION_002")
  void getImagesStatus_returns400_whenIdsMissing() throws Exception {
    mockMvc
        .perform(get(URL).with(authentication(authAs(1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_002"));
  }

  @Test
  @DisplayName("invalid ids request returns 400 VALIDATION_001")
  void getImagesStatus_returns400_whenIdsInvalid() throws Exception {
    given(getImagesStatusUseCase.execute(any()))
        .willThrow(new IllegalArgumentException("ids must contain at most 10 items"));

    mockMvc
        .perform(
            get(URL)
                .with(authentication(authAs(1L)))
                .param("ids", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("blank ids query returns 400 VALIDATION_001")
  void getImagesStatus_returns400_whenIdsBlank() throws Exception {
    mockMvc
        .perform(get(URL).with(authentication(authAs(1L))).param("ids", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("success response preserves lookup statuses")
  void getImagesStatus_returns200_withLookupStatuses() throws Exception {
    given(getImagesStatusUseCase.execute(any()))
        .willReturn(
            new GetImagesStatusResult(
                List.of(
                    new ImageLookupItem(101L, LookupStatus.PENDING),
                    new ImageLookupItem(102L, LookupStatus.COMPLETED),
                    new ImageLookupItem(999L, LookupStatus.NOT_FOUND))));

    mockMvc
        .perform(get(URL).with(authentication(authAs(1L))).param("ids", "101", "102", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.images.length()").value(3))
        .andExpect(jsonPath("$.data.images[0].imageId").value(101))
        .andExpect(jsonPath("$.data.images[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.images[1].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.images[2].status").value("NOT_FOUND"));
  }
}
