package momzzangseven.mztkbe.modules.tag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.tag.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.tag.application.port.out.SaveTagPort;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagService unit test")
class TagServiceTest {

  @Mock private LoadTagPort loadTagPort;
  @Mock private SaveTagPort saveTagPort;

  @InjectMocks private TagService tagService;

  @Test
  @DisplayName("linkTagsToPost returns immediately for null or empty input")
  void linkTagsToPostReturnsForEmptyInput() {
    tagService.linkTagsToPost(1L, null);
    tagService.linkTagsToPost(1L, List.of());

    verifyNoInteractions(loadTagPort, saveTagPort);
  }

  @Test
  @DisplayName("linkTagsToPost normalizes names, creates missing tags, and saves mappings")
  void linkTagsToPostNormalizesAndSaves() {
    List<String> input = List.of(" Java ", "java", "Spring ");
    Tag existingJava = Tag.builder().id(10L).name("java").build();
    Tag savedSpring = Tag.builder().id(20L).name("spring").build();

    when(loadTagPort.loadTagsByNames(List.of("java", "spring"))).thenReturn(List.of(existingJava));
    when(saveTagPort.saveTags(anyList())).thenReturn(List.of(savedSpring));

    tagService.linkTagsToPost(99L, input);

    ArgumentCaptor<List<Tag>> newTagsCaptor = ArgumentCaptor.forClass(List.class);
    verify(saveTagPort).saveTags(newTagsCaptor.capture());
    assertThat(newTagsCaptor.getValue()).hasSize(1);
    assertThat(newTagsCaptor.getValue().getFirst().getName()).isEqualTo("spring");

    ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(saveTagPort)
        .savePostTagMappings(org.mockito.ArgumentMatchers.eq(99L), idsCaptor.capture());
    assertThat(idsCaptor.getValue()).containsExactly(10L, 20L);
  }

  @Test
  @DisplayName("linkTagsToPost skips saveTags when all names already exist")
  void linkTagsToPostSkipsSaveForExistingTags() {
    Tag existingJava = Tag.builder().id(10L).name("java").build();
    when(loadTagPort.loadTagsByNames(List.of("java"))).thenReturn(List.of(existingJava));

    tagService.linkTagsToPost(50L, List.of("JAVA", " java "));

    verify(saveTagPort, never()).saveTags(anyList());
    verify(saveTagPort).savePostTagMappings(50L, List.of(10L));
  }

  @Test
  @DisplayName("updateTags deletes old mapping and relinks when new tag names exist")
  void updateTagsWithNames() {
    Tag saved = Tag.builder().id(7L).name("kotlin").build();
    when(loadTagPort.loadTagsByNames(List.of("kotlin"))).thenReturn(List.of());
    when(saveTagPort.saveTags(anyList())).thenReturn(List.of(saved));

    tagService.updateTags(11L, List.of("kotlin"));

    verify(saveTagPort).deleteTagsByPostId(11L);
    verify(saveTagPort).savePostTagMappings(11L, List.of(7L));
  }

  @Test
  @DisplayName("updateTags only deletes when incoming tag names are null")
  void updateTagsWithNullNames() {
    tagService.updateTags(12L, null);

    verify(saveTagPort).deleteTagsByPostId(12L);
    verifyNoInteractions(loadTagPort);
    verify(saveTagPort, never()).saveTags(anyList());
  }

  @Test
  @DisplayName("deleteTagsByPostId delegates to save port")
  void deleteTagsByPostIdDelegates() {
    tagService.deleteTagsByPostId(13L);

    verify(saveTagPort).deleteTagsByPostId(13L);
  }
}
