package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminBoardPostEnumMapper 단위 테스트")
class AdminBoardPostEnumMapperTest {

  @Test
  @DisplayName("admin board post enum 은 post enum 과 1:1 이름 계약을 유지한다")
  void adminPostEnumsMirrorPostEnums() {
    assertThat(names(AdminBoardPostPublicationStatus.values()))
        .containsExactlyElementsOf(names(PostPublicationStatus.values()));
    assertThat(names(AdminBoardPostModerationStatus.values()))
        .containsExactlyElementsOf(names(PostModerationStatus.values()));
    assertThat(names(AdminBoardPostType.values()))
        .containsExactlyElementsOf(names(PostType.values()));
    assertThat(names(AdminBoardType.values())).containsExactlyElementsOf(names(PostType.values()));
    assertThat(names(AdminBoardPostStatus.values()))
        .containsExactlyElementsOf(names(PostStatus.values()));
  }

  @Test
  @DisplayName("post publicationStatus 전체 값을 admin publicationStatus 로 매핑한다")
  void toAdminPublicationStatus_mapsAllValues() {
    assertThat(
            Arrays.stream(PostPublicationStatus.values())
                .map(AdminBoardPostEnumMapper::toAdminPublicationStatus))
        .containsExactly(
            AdminBoardPostPublicationStatus.PENDING,
            AdminBoardPostPublicationStatus.VISIBLE,
            AdminBoardPostPublicationStatus.FAILED);
  }

  @Test
  @DisplayName("admin publicationStatus filter 전체 값을 post publicationStatus 로 매핑한다")
  void toPostPublicationStatus_mapsAllValues() {
    assertThat(
            Arrays.stream(AdminBoardPostPublicationStatus.values())
                .map(AdminBoardPostEnumMapper::toPostPublicationStatus))
        .containsExactly(
            PostPublicationStatus.PENDING,
            PostPublicationStatus.VISIBLE,
            PostPublicationStatus.FAILED);
    assertThat(AdminBoardPostEnumMapper.toPostPublicationStatus(null)).isNull();
  }

  @Test
  @DisplayName("post moderationStatus 전체 값을 admin moderationStatus 로 매핑한다")
  void toAdminModerationStatus_mapsAllValues() {
    assertThat(
            Arrays.stream(PostModerationStatus.values())
                .map(AdminBoardPostEnumMapper::toAdminModerationStatus))
        .containsExactly(
            AdminBoardPostModerationStatus.NORMAL, AdminBoardPostModerationStatus.BLOCKED);
  }

  @Test
  @DisplayName("admin moderationStatus filter 전체 값을 post moderationStatus 로 매핑한다")
  void toPostModerationStatus_mapsAllValues() {
    assertThat(
            Arrays.stream(AdminBoardPostModerationStatus.values())
                .map(AdminBoardPostEnumMapper::toPostModerationStatus))
        .containsExactly(PostModerationStatus.NORMAL, PostModerationStatus.BLOCKED);
    assertThat(AdminBoardPostEnumMapper.toPostModerationStatus(null)).isNull();
  }

  @Test
  @DisplayName("post type 전체 값을 admin board type 으로 매핑한다")
  void toAdminPostType_mapsAllValues() {
    assertThat(Arrays.stream(PostType.values()).map(AdminBoardPostEnumMapper::toAdminPostType))
        .containsExactly(AdminBoardPostType.FREE, AdminBoardPostType.QUESTION);
    assertThat(Arrays.stream(PostType.values()).map(AdminBoardPostEnumMapper::toAdminBoardType))
        .containsExactly(AdminBoardType.FREE, AdminBoardType.QUESTION);
  }

  @Test
  @DisplayName("admin type filter 전체 값을 post type 으로 매핑한다")
  void toPostType_mapsAllValues() {
    assertThat(Arrays.stream(AdminBoardPostType.values()).map(AdminBoardPostEnumMapper::toPostType))
        .containsExactly(PostType.FREE, PostType.QUESTION);
    assertThat(AdminBoardPostEnumMapper.toPostType(null)).isNull();
  }

  @Test
  @DisplayName("post status 전체 값을 admin post status 로 매핑한다")
  void toAdminPostStatus_mapsAllValues() {
    assertThat(Arrays.stream(PostStatus.values()).map(AdminBoardPostEnumMapper::toAdminPostStatus))
        .containsExactly(
            AdminBoardPostStatus.OPEN,
            AdminBoardPostStatus.PENDING_ACCEPT,
            AdminBoardPostStatus.PENDING_ADMIN_REFUND,
            AdminBoardPostStatus.RESOLVED);
  }

  @Test
  @DisplayName("admin status filter 전체 값을 post status 로 매핑한다")
  void toPostStatus_mapsAllValues() {
    assertThat(
            Arrays.stream(AdminBoardPostStatus.values())
                .map(AdminBoardPostEnumMapper::toPostStatus))
        .containsExactly(
            PostStatus.OPEN,
            PostStatus.PENDING_ACCEPT,
            PostStatus.PENDING_ADMIN_REFUND,
            PostStatus.RESOLVED);
    assertThat(AdminBoardPostEnumMapper.toPostStatus(null)).isNull();
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
