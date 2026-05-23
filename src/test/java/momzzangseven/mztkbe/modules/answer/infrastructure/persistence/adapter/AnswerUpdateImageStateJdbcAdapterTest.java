package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("AnswerUpdateImageStateJdbcAdapter")
class AnswerUpdateImageStateJdbcAdapterTest {

  private JdbcTemplate jdbcTemplate;
  private AnswerUpdateImageStateJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl(
        "jdbc:h2:mem:"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");
    jdbcTemplate = new JdbcTemplate(dataSource);
    adapter = new AnswerUpdateImageStateJdbcAdapter(jdbcTemplate);
    createSchema();
  }

  @Test
  @DisplayName("replacePendingImages replaces existing rows and preserves requested order")
  void replacePendingImages_replacesRows() {
    insertUpdateState(1L, 10L, "DISCARDED");
    adapter.replacePendingImages(1L, List.of(30L, 20L));

    assertThat(adapter.loadPendingImageIds(1L)).containsExactly(30L, 20L);

    adapter.replacePendingImages(1L, List.of(40L));

    assertThat(adapter.loadPendingImageIds(1L)).containsExactly(40L);
  }

  @Test
  @DisplayName("markPendingImageUpdate sets pending image flag")
  void markPendingImageUpdate_setsFlag() {
    insertUpdateState(1L, 10L, "DISCARDED");

    adapter.markPendingImageUpdate(1L);

    assertThat(adapter.hasPendingImageUpdate(1L)).isTrue();
  }

  @Test
  @DisplayName("findDiscardedUpdateStateIds returns discarded states for answer")
  void findDiscardedUpdateStateIds_returnsOnlyDiscarded() {
    insertUpdateState(1L, 10L, "DISCARDED");
    insertUpdateState(2L, 10L, "FAILED");
    insertUpdateState(3L, 11L, "DISCARDED");

    assertThat(adapter.findDiscardedUpdateStateIds(10L)).containsExactly(1L);
  }

  private void createSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_update_states (
            id BIGINT PRIMARY KEY,
            answer_id BIGINT NOT NULL,
            status VARCHAR(40) NOT NULL,
            pending_image_update BOOLEAN NOT NULL DEFAULT FALSE,
            updated_at TIMESTAMP
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_update_images (
            update_state_id BIGINT NOT NULL,
            image_id BIGINT NOT NULL,
            image_order INTEGER NOT NULL,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            PRIMARY KEY (update_state_id, image_id)
        )
        """);
  }

  private void insertUpdateState(Long id, Long answerId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_update_states (
            id,
            answer_id,
            status,
            pending_image_update,
            updated_at
        ) VALUES (?, ?, ?, false, NOW())
        """,
        id,
        answerId,
        status);
  }
}
