package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("GetRepliesQuery unit test")
class GetRepliesQueryTest {

  @Test
  @DisplayName("record stores parentId and pageable")
  void record_storesFields() {
    Pageable pageable = PageRequest.of(1, 20);

    GetRepliesQuery query = new GetRepliesQuery(99L, pageable);

    assertThat(query.parentId()).isEqualTo(99L);
    assertThat(query.pageable()).isEqualTo(pageable);
  }

  @Test
  @DisplayName("record allows null values")
  void record_allowsNullValues() {
    GetRepliesQuery query = new GetRepliesQuery(null, null);

    assertThat(query.parentId()).isNull();
    assertThat(query.pageable()).isNull();
  }
}
