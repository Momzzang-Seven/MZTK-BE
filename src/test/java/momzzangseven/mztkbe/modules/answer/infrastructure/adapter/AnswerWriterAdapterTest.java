package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerWriterAdapter unit test")
class AnswerWriterAdapterTest {

  @Mock private LoadUserPort loadUserPort;

  @InjectMocks private AnswerWriterAdapter answerWriterAdapter;

  @Test
  @DisplayName("loadWritersByIds() maps users to WriterSummary map")
  void loadWritersByIds_mapsUsersToWriterSummary() {
    User firstUser =
        mock(
            User.class,
            invocation ->
                Map.of(
                        "getId", 1L,
                        "getNickname", "writer-a",
                        "getProfileImageUrl", "https://cdn.example.com/a.webp")
                    .get(invocation.getMethod().getName()));
    User secondUser =
        mock(
            User.class,
            invocation ->
                Map.of(
                        "getId", 2L,
                        "getNickname", "writer-b",
                        "getProfileImageUrl", "https://cdn.example.com/b.webp")
                    .get(invocation.getMethod().getName()));

    when(loadUserPort.loadUsersByIds(List.of(1L, 2L))).thenReturn(List.of(firstUser, secondUser));

    Map<Long, WriterSummary> result = answerWriterAdapter.loadWritersByIds(List.of(1L, 2L));

    assertThat(result)
        .containsEntry(1L, new WriterSummary(1L, "writer-a", "https://cdn.example.com/a.webp"))
        .containsEntry(2L, new WriterSummary(2L, "writer-b", "https://cdn.example.com/b.webp"));
  }
}
