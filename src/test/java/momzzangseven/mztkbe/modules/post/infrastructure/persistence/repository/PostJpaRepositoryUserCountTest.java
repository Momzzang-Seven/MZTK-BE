package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("PostJpaRepository user-count DataJpaTest")
class PostJpaRepositoryUserCountTest {

  @Autowired private TestEntityManager em;

  @Autowired private PostJpaRepository postJpaRepository;

  @Test
  @DisplayName("countPostsByUserIds() 는 현재 존재하는 post row 수를 userId 별로 집계한다")
  void countPostsByUserIds_returnsCounts() {
    em.persist(
        PostEntity.builder()
            .userId(10L)
            .type(PostType.FREE)
            .content("a")
            .status(PostStatus.OPEN)
            .build());
    em.persist(
        PostEntity.builder()
            .userId(10L)
            .type(PostType.FREE)
            .content("b")
            .status(PostStatus.OPEN)
            .build());
    em.persist(
        PostEntity.builder()
            .userId(11L)
            .type(PostType.QUESTION)
            .title("q")
            .content("c")
            .reward(1L)
            .status(PostStatus.OPEN)
            .build());
    em.persist(
        PostEntity.builder()
            .userId(12L)
            .type(PostType.FREE)
            .content("d")
            .status(PostStatus.OPEN)
            .build());
    em.flush();

    Map<Long, Long> counts =
        postJpaRepository.countPostsByUserIds(java.util.List.of(10L, 11L)).stream()
            .collect(
                Collectors.toMap(
                    PostJpaRepository.UserPostCount::getUserId,
                    PostJpaRepository.UserPostCount::getPostCount));

    assertThat(counts).containsEntry(10L, 2L).containsEntry(11L, 1L).doesNotContainKey(12L);
  }
}
