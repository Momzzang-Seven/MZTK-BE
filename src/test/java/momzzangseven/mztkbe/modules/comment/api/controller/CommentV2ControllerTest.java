package momzzangseven.mztkbe.modules.comment.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.DeleteCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentCursorUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.UpdateCommentUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("CommentV2Controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
class CommentV2ControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private CreateCommentUseCase createCommentUseCase;
  @MockitoBean private GetCommentUseCase getCommentUseCase;
  @MockitoBean private GetCommentCursorUseCase getCommentCursorUseCase;
  @MockitoBean private UpdateCommentUseCase updateCommentUseCase;
  @MockitoBean private DeleteCommentUseCase deleteCommentUseCase;

  @Test
  @DisplayName("GET /v2/posts/{postId}/comments returns cursor metadata")
  void getRootCommentsV2_success() throws Exception {
    given(getCommentCursorUseCase.getRootCommentsByCursor(any()))
        .willReturn(new GetCommentsCursorResult(List.of(comment(21L, "댓글", false)), true, "next"));

    mockMvc
        .perform(get("/v2/posts/10/comments?size=1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.comments[0].commentId").value(21))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next"));
  }

  @Test
  @DisplayName("GET /v2/comments/{commentId}/replies returns cursor metadata")
  void getRepliesV2_success() throws Exception {
    given(getCommentCursorUseCase.getRepliesByCursor(any()))
        .willReturn(new GetCommentsCursorResult(List.of(comment(22L, "대댓글", false)), false, null));

    mockMvc
        .perform(get("/v2/comments/5/replies").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.comments[0].commentId").value(22))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
  }

  @Test
  @DisplayName("GET /v2/answers/{answerId}/comments returns cursor metadata")
  void getAnswerRootCommentsV2_success() throws Exception {
    given(getCommentCursorUseCase.getAnswerRootCommentsByCursor(any()))
        .willReturn(
            new GetCommentsCursorResult(List.of(comment(31L, "답변 댓글", false)), false, null));

    mockMvc
        .perform(get("/v2/answers/300/comments?size=1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.comments[0].commentId").value(31))
        .andExpect(jsonPath("$.data.comments[0].content").value("답변 댓글"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
  }

  @Test
  @DisplayName("POST /v2/answers/{answerId}/comments creates answer comment")
  void createAnswerCommentV2_success() throws Exception {
    given(createCommentUseCase.createComment(any(CreateCommentCommand.class)))
        .willReturn(mutationResult(32L, "답변 댓글", null, false));

    mockMvc
        .perform(
            post("/v2/answers/300/comments")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "답변 댓글"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.commentId").value(32))
        .andExpect(jsonPath("$.data.content").value("답변 댓글"));
  }

  @Test
  @DisplayName("PUT /v2/answers/{answerId}/comments/{commentId} updates answer comment")
  void updateAnswerCommentV2_success() throws Exception {
    given(updateCommentUseCase.updateAnswerComment(any(UpdateAnswerCommentCommand.class)))
        .willReturn(mutationResult(32L, "수정된 답변 댓글", null, false));

    mockMvc
        .perform(
            put("/v2/answers/300/comments/32")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "수정된 답변 댓글"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.commentId").value(32))
        .andExpect(jsonPath("$.data.content").value("수정된 답변 댓글"));

    verify(updateCommentUseCase).updateAnswerComment(any(UpdateAnswerCommentCommand.class));
  }

  @Test
  @DisplayName("PUT /v2/answers/{answerId}/comments/{commentId} returns 400 for blank content")
  void updateAnswerCommentV2_blankContent_returns400() throws Exception {
    mockMvc
        .perform(
            put("/v2/answers/300/comments/32")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", " "))))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(updateCommentUseCase);
  }

  @Test
  @DisplayName("DELETE /v2/answers/{answerId}/comments/{commentId} deletes answer comment")
  void deleteAnswerCommentV2_success() throws Exception {
    mockMvc
        .perform(delete("/v2/answers/300/comments/32").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(deleteCommentUseCase).deleteAnswerComment(any(DeleteAnswerCommentCommand.class));
  }

  @Test
  @DisplayName("GET /v2/posts/{postId}/comments returns 400 for malformed cursor")
  void getRootCommentsV2_malformedCursor_returns400() throws Exception {
    mockMvc
        .perform(get("/v2/posts/10/comments?cursor=%%%").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(getCommentCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/posts/{postId}/comments returns 401 when unauthenticated")
  void getRootCommentsV2_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/v2/posts/10/comments")).andExpect(status().isUnauthorized());

    verifyNoInteractions(getCommentCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/comments/{commentId}/replies returns 401 when unauthenticated")
  void getRepliesV2_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/v2/comments/5/replies")).andExpect(status().isUnauthorized());

    verifyNoInteractions(getCommentCursorUseCase);
  }

  private CommentResult comment(Long id, String content, boolean isDeleted) {
    LocalDateTime now = LocalDateTime.now();
    return new CommentResult(
        id, content, 1L, "writer-1", "profile-1", null, 1L, isDeleted, now, now);
  }

  private CommentMutationResult mutationResult(
      Long id, String content, Long parentId, boolean isDeleted) {
    LocalDateTime now = LocalDateTime.now();
    return new CommentMutationResult(id, content, 1L, parentId, isDeleted, now, now);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    List<SimpleGrantedAuthority> grantedAuthorities =
        List.of(new SimpleGrantedAuthority("ROLE_USER"));
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }
}
