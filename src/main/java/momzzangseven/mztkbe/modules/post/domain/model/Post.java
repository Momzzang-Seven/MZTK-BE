package momzzangseven.mztkbe.modules.post.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId; // 작성자 ID

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostType type; // 'FREE', 'QUESTION'

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(nullable = false)
  private int viewCount = 0;

  // --- 질문 게시판 전용 필드 (Nullable) ---

  @Column(nullable = true)
  private Long reward; // 채택 보상 토큰 (FREE일 경우 null)

  @Column(nullable = true)
  private Boolean isSolved; // 해결 여부 (FREE일 경우 null)

  // --- Audit (생성/수정 시간) ---

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate private LocalDateTime updatedAt;

  // --- 생성자 (Builder) ---
  @Builder
  public Post(Long userId, PostType type, String title, String content, Long reward) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.viewCount = 0;

    // 타입에 따른 필드 초기화
    if (type == PostType.QUESTION) {
      this.reward = reward;
      this.isSolved = false; // 질문 생성 시 기본값은 미해결
    } else {
      this.reward = null;
      this.isSolved = null;
    }
  }

  // --- 비즈니스 로직 메서드 ---

  /** 게시글 수정 (제목, 내용) 질문 게시판이고 이미 해결된 경우 수정 불가 로직은 서비스 계층에서 검증 권장 */
  public void update(String title, String content) {
    this.title = title;
    this.content = content;
  }

  /** 조회수 증가 */
  public void increaseViewCount() {
    this.viewCount++;
  }

  /** 질문 해결 처리 (답변 채택 시 호출) */
  public void markAsSolved() {
    if (this.type != PostType.QUESTION) {
      throw new IllegalStateException("자유게시글은 해결 상태를 가질 수 없습니다.");
    }
    this.isSolved = true;
  }

  // 이 게시글이 질문인지 확인하는 편의 메서드
  public boolean isQuestion() {
    return this.type == PostType.QUESTION;
  }
}
