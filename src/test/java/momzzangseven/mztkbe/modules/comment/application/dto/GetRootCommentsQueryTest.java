package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("GetRootCommentsQuery unit test")
class GetRootCommentsQueryTest {

  @Test
  @DisplayName("record stores postId and pageable")
  void record_storesFields() {
    Pageable pageable = PageRequest.of(0, 10);

    GetRootCommentsQuery query = new GetRootCommentsQuery(77L, pageable);

    assertThat(query.postId()).isEqualTo(77L);
    assertThat(query.pageable()).isEqualTo(pageable);
  }

  @Test
  @DisplayName("record allows null values")
  void record_allowsNullValues() {
    GetRootCommentsQuery query = new GetRootCommentsQuery(null, null);

    assertThat(query.postId()).isNull();
    assertThat(query.pageable()).isNull();
  }
}
