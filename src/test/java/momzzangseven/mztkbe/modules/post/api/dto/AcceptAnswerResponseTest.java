package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AcceptAnswerResponse unit test")
class AcceptAnswerResponseTest {

  @Test
  @DisplayName("from(AcceptAnswerResult) maps accept metadata")
  void from_mapsFields() {
    AcceptAnswerResponse response =
        AcceptAnswerResponse.from(
            new AcceptAnswerResult(10L, 20L, PostStatus.PENDING_ACCEPT, null));

    assertThat(response.postId()).isEqualTo(10L);
    assertThat(response.acceptedAnswerId()).isEqualTo(20L);
    assertThat(response.status()).isEqualTo(PostStatus.PENDING_ACCEPT);
    assertThat(response.web3()).isNull();
  }
}
