package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.PostTagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.TagEntity;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.PostTagJpaRepository;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository.TagJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagPersistenceAdapter unit test")
class TagPersistenceAdapterTest {

  @Mock private TagJpaRepository tagJpaRepository;
  @Mock private PostTagJpaRepository postTagJpaRepository;

  @InjectMocks private TagPersistenceAdapter tagPersistenceAdapter;

  @Test
  @DisplayName("loadTagsByNames maps entities to domain")
  void loadTagsByNamesMapsToDomain() {
    TagEntity java = new TagEntity("java");
    TagEntity spring = new TagEntity("spring");
    ReflectionTestUtils.setField(java, "id", 1L);
    ReflectionTestUtils.setField(spring, "id", 2L);

    when(tagJpaRepository.findByNameIn(List.of("java", "spring")))
        .thenReturn(List.of(java, spring));

    List<Tag> result = tagPersistenceAdapter.loadTagsByNames(List.of("java", "spring"));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(1L);
    assertThat(result.get(0).getName()).isEqualTo("java");
    assertThat(result.get(1).getId()).isEqualTo(2L);
    assertThat(result.get(1).getName()).isEqualTo("spring");
  }

  @Test
  @DisplayName("saveTags converts domain tags and maps saved entities back")
  void saveTagsMapsBothDirections() {
    Tag tag = Tag.builder().id(null).name("java").build();

    TagEntity saved = new TagEntity("java");
    ReflectionTestUtils.setField(saved, "id", 99L);

    when(tagJpaRepository.saveAll(anyList())).thenReturn(List.of(saved));

    List<Tag> result = tagPersistenceAdapter.saveTags(List.of(tag));

    ArgumentCaptor<List<TagEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(tagJpaRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().getFirst().getName()).isEqualTo("java");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo(99L);
    assertThat(result.getFirst().getName()).isEqualTo("java");
  }

  @Test
  @DisplayName("savePostTagMappings creates mapping entities for every tag ID")
  void savePostTagMappingsCreatesEntities() {
    tagPersistenceAdapter.savePostTagMappings(7L, List.of(10L, 11L));

    ArgumentCaptor<List<PostTagEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(postTagJpaRepository).saveAll(captor.capture());

    List<PostTagEntity> mappings = captor.getValue();
    assertThat(mappings).hasSize(2);
    assertThat(mappings.get(0).getPostId()).isEqualTo(7L);
    assertThat(mappings.get(0).getTagId()).isEqualTo(10L);
    assertThat(mappings.get(1).getPostId()).isEqualTo(7L);
    assertThat(mappings.get(1).getTagId()).isEqualTo(11L);
  }

  @Test
  @DisplayName("deleteTagsByPostId delegates to repository")
  void deleteTagsByPostIdDelegates() {
    tagPersistenceAdapter.deleteTagsByPostId(8L);

    verify(postTagJpaRepository).deleteByPostId(8L);
  }
}
