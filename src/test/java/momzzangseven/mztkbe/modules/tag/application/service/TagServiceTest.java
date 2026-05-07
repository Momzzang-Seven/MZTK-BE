package momzzangseven.mztkbe.modules.tag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
  @DisplayName("linkTagsToPost filters blank and null tag names before lookup")
  void linkTagsToPost_filtersBlankAndNullNames() {
    Tag existingJava = Tag.builder().id(10L).name("java").build();
    Tag existingSpring = Tag.builder().id(20L).name("spring").build();

    when(loadTagPort.loadTagsByNames(List.of("java", "spring")))
        .thenReturn(List.of(existingJava, existingSpring));

    tagService.linkTagsToPost(77L, Arrays.asList(" Java ", " ", null, "Spring "));

    verify(saveTagPort).saveTagNamesIfAbsent(List.of("java", "spring"));
    verify(loadTagPort).loadTagsByNames(List.of("java", "spring"));
    verify(saveTagPort).savePostTagMappings(77L, List.of(10L, 20L));
  }

  @Test
  @DisplayName("linkTagsToPost returns immediately when all tag names are blank")
  void linkTagsToPost_blankOnlyInput_returnsImmediately() {
    tagService.linkTagsToPost(88L, Arrays.asList(" ", "", null, "   "));

    verifyNoInteractions(loadTagPort);
    verify(saveTagPort, never()).saveTags(anyList());
    verify(saveTagPort, never()).saveTagNamesIfAbsent(anyList());
    verify(saveTagPort, never()).savePostTagMappings(eq(88L), anyList());
  }

  @Test
  @DisplayName("linkTagsToPost normalizes names, creates missing tags, and saves mappings")
  void linkTagsToPostNormalizesAndSaves() {
    List<String> input = List.of(" Java ", "java", "Spring ");
    Tag existingJava = Tag.builder().id(10L).name("java").build();
    Tag existingSpring = Tag.builder().id(20L).name("spring").build();

    when(loadTagPort.loadTagsByNames(List.of("java", "spring")))
        .thenReturn(List.of(existingJava, existingSpring));

    tagService.linkTagsToPost(99L, input);

    verify(saveTagPort).saveTagNamesIfAbsent(List.of("java", "spring"));
    verify(saveTagPort, never()).saveTags(anyList());

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

    verify(saveTagPort).saveTagNamesIfAbsent(List.of("java"));
    verify(saveTagPort, never()).saveTags(anyList());
    verify(saveTagPort).savePostTagMappings(50L, List.of(10L));
  }

  @Test
  @DisplayName("updateTags deletes old mapping and relinks when new tag names exist")
  void updateTagsWithNames() {
    Tag saved = Tag.builder().id(7L).name("kotlin").build();
    when(loadTagPort.loadTagsByNames(List.of("kotlin"))).thenReturn(List.of(saved));
    when(loadTagPort.loadTagIdsByPostId(11L)).thenReturn(List.of(3L));

    tagService.updateTags(11L, List.of("kotlin"));

    verify(saveTagPort).saveTagNamesIfAbsent(List.of("kotlin"));
    verify(saveTagPort).deletePostTagMappings(11L, List.of(3L));
    verify(saveTagPort).savePostTagMappings(11L, List.of(7L));
  }

  @Test
  @DisplayName("updateTags only deletes when incoming tag names are null")
  void updateTagsWithNullNames() {
    when(loadTagPort.loadTagIdsByPostId(12L)).thenReturn(List.of(1L, 2L));

    tagService.updateTags(12L, null);

    verify(saveTagPort).deletePostTagMappings(12L, List.of(1L, 2L));
    verify(saveTagPort, never()).saveTags(anyList());
    verify(saveTagPort, never()).savePostTagMappings(eq(12L), anyList());
  }

  @Test
  @DisplayName("updateTags only deletes when incoming tag names are blank")
  void updateTagsWithBlankNames() {
    when(loadTagPort.loadTagIdsByPostId(14L)).thenReturn(List.of(1L));

    tagService.updateTags(14L, Arrays.asList(" ", null, ""));

    verify(saveTagPort).deletePostTagMappings(14L, List.of(1L));
    verify(saveTagPort, never()).saveTags(anyList());
    verify(saveTagPort, never()).savePostTagMappings(eq(14L), anyList());
  }

  @Test
  @DisplayName("deleteTagsByPostId delegates to save port")
  void deleteTagsByPostIdDelegates() {
    tagService.deleteTagsByPostId(13L);

    verify(saveTagPort).deleteTagsByPostId(13L);
  }
}
