package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

/** Domain model for XP grant policy per XpType. */
@Getter
@Builder(toBuilder = true)
public class XpPolicy {
  public static final int UNLIMITED_DAILY_CAP = -1;

  private Long id;
  private XpType type;
  private int xpAmount;
  private int dailyCap;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  private boolean enabled;

  public boolean isUnlimited() {
    return dailyCap == UNLIMITED_DAILY_CAP;
  }
}
