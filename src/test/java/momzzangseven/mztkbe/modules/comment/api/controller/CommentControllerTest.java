package momzzangseven.mztkbe.modules.comment.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.DeleteCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.UpdateCommentUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("CommentController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class CommentControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private CreateCommentUseCase createCommentUseCase;
  @MockitoBean private GetCommentUseCase getCommentUseCase;
  @MockitoBean private UpdateCommentUseCase updateCommentUseCase;
  @MockitoBean private DeleteCommentUseCase deleteCommentUseCase;

  @Nested
  @DisplayName("POST /posts/{postId}/comments")
  class CreateComment {

    @Test
    @DisplayName("정상 요청이면 200과 댓글 데이터를 반환한다")
    void createComment_success() throws Exception {
      given(createCommentUseCase.createComment(any(CreateCommentCommand.class)))
          .willReturn(comment(1L, "첫 댓글", false));

      mockMvc
          .perform(
              post("/posts/10/comments")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "첫 댓글"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.commentId").value(1))
          .andExpect(jsonPath("$.data.content").value("첫 댓글"));

      verify(createCommentUseCase).createComment(any(CreateCommentCommand.class));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void createComment_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(
              post("/posts/10/comments")
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "댓글"))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 객체에 userId가 null이면 401을 반환한다")
    void createComment_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(
              post("/posts/10/comments")
                  .with(nullUserPrincipal())
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "댓글"))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내용이 공백이면 400을 반환한다")
    void createComment_blankContent_returns400() throws Exception {
      mockMvc
          .perform(
              post("/posts/10/comments")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "   "))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("긴 댓글도 생성 요청을 통과한다")
    void createComment_longContent_returns200() throws Exception {
      String longContent = "a".repeat(5000);
      given(createCommentUseCase.createComment(any(CreateCommentCommand.class)))
          .willReturn(comment(2L, longContent, false));
      mockMvc
          .perform(
              post("/posts/10/comments")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", longContent))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.commentId").value(2))
          .andExpect(jsonPath("$.data.content").value(longContent));

      verify(createCommentUseCase).createComment(any(CreateCommentCommand.class));
    }
  }

  @Nested
  @DisplayName("PUT /comments/{commentId}")
  class UpdateComment {

    @Test
    @DisplayName("정상 수정이면 200을 반환한다")
    void updateComment_success() throws Exception {
      given(updateCommentUseCase.updateComment(any(UpdateCommentCommand.class)))
          .willReturn(comment(7L, "수정된 댓글", false));

      mockMvc
          .perform(
              put("/comments/7")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "수정된 댓글"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.commentId").value(7))
          .andExpect(jsonPath("$.data.content").value("수정된 댓글"));
    }

    @Test
    @DisplayName("수정 내용이 공백이면 400을 반환한다")
    void updateComment_blankContent_returns400() throws Exception {
      mockMvc
          .perform(
              put("/comments/7")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", ""))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("긴 댓글도 수정 요청을 통과한다")
    void updateComment_longContent_returns200() throws Exception {
      String longContent = "a".repeat(5000);
      given(updateCommentUseCase.updateComment(any(UpdateCommentCommand.class)))
          .willReturn(comment(7L, longContent, false));

      mockMvc
          .perform(
              put("/comments/7")
                  .with(userPrincipal(1L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", longContent))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.commentId").value(7))
          .andExpect(jsonPath("$.data.content").value(longContent));

      verify(updateCommentUseCase).updateComment(any(UpdateCommentCommand.class));
    }

    @Test
    @DisplayName("인증 없이 수정 요청하면 401을 반환한다")
    void updateComment_unauthenticated_returns401() throws Exception {
      mockMvc.perform(put("/comments/7")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("수정 요청 principal이 null이면 401을 반환한다")
    void updateComment_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(
              put("/comments/7")
                  .with(nullUserPrincipal())
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "수정"))))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("DELETE /comments/{commentId}")
  class DeleteComment {

    @Test
    @DisplayName("정상 삭제면 200을 반환한다")
    void deleteComment_success() throws Exception {
      mockMvc
          .perform(delete("/comments/3").with(userPrincipal(1L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("인증 없이 삭제 요청하면 401을 반환한다")
    void deleteComment_unauthenticated_returns401() throws Exception {
      mockMvc.perform(delete("/comments/3")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("삭제 요청 principal이 null이면 401을 반환한다")
    void deleteComment_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(delete("/comments/3").with(nullUserPrincipal()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET 조회")
  class QueryComments {

    @Test
    @DisplayName("루트 댓글 조회 시 삭제 댓글은 마스킹된다")
    void getRootComments_deletedCommentMasked() throws Exception {
      given(getCommentUseCase.getRootComments(any(GetRootCommentsQuery.class)))
          .willReturn(
              new PageImpl<>(
                  java.util.List.of(comment(11L, "원문", true)), PageRequest.of(0, 20), 1));

      mockMvc
          .perform(get("/posts/10/comments").with(userPrincipal(1L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.content[0].commentId").value(11))
          .andExpect(jsonPath("$.data.content[0].isDeleted").value(true))
          .andExpect(jsonPath("$.data.content[0].content").value("삭제된 댓글입니다."));
    }

    @Test
    @DisplayName("인증 없이 루트 댓글 조회 시 401을 반환한다")
    void getRootComments_unauthenticated_returns401() throws Exception {
      mockMvc.perform(get("/posts/10/comments")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("대댓글 조회는 200을 반환한다")
    void getReplies_success() throws Exception {
      given(getCommentUseCase.getReplies(any(GetRepliesQuery.class)))
          .willReturn(new PageImpl<>(java.util.List.of(comment(12L, "대댓글", false))));

      mockMvc
          .perform(get("/comments/5/replies").with(userPrincipal(1L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.content[0].commentId").value(12));
    }

    @Test
    @DisplayName("인증 없이 대댓글 조회 시 401을 반환한다")
    void getReplies_unauthenticated_returns401() throws Exception {
      mockMvc.perform(get("/comments/5/replies")).andExpect(status().isUnauthorized());
    }
  }

  private CommentResult comment(Long id, String content, boolean isDeleted) {
    LocalDateTime now = LocalDateTime.now();
    return new CommentResult(id, content, 1L, null, isDeleted, now, now);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
