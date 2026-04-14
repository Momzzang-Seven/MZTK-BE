package momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** JPA entity mapping to the images table. */
@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ImageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "reference_type", length = 30)
  private String referenceType;

  @Column(name = "reference_id")
  private Long referenceId;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "tmp_object_key", nullable = false, unique = true, length = 512)
  private String tmpObjectKey;

  @Column(name = "final_object_key", unique = true, length = 512)
  private String finalObjectKey;

  @Column(name = "img_order")
  private Integer imgOrder;

  @Column(name = "error_reason", length = 1024)
  private String errorReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
