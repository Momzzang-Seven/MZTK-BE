package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateCommentRequest unit test")
class UpdateCommentRequestTest {

  @Test
  @DisplayName("record constructor stores content and getter returns it")
  void constructorAndGetter_storeAndReturnContent() {
    UpdateCommentRequest request = new UpdateCommentRequest("updated content");

    assertThat(request.content()).isEqualTo("updated content");
  }
}
