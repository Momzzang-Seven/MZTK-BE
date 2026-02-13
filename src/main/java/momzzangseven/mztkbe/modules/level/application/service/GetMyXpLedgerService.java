package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyXpLedgerResult;
import momzzangseven.mztkbe.modules.level.application.dto.XpDailyCapStatusItem;
import momzzangseven.mztkbe.modules.level.application.dto.XpLedgerEntryItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyXpLedgerUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyXpLedgerService implements GetMyXpLedgerUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final XpLedgerPort xpLedgerPort;
  private final PolicyPort policyPort;
  private final ZoneId appZoneId;

  @Override
  public GetMyXpLedgerResult execute(Long userId, int page, int size) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size <= 0 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    List<XpLedgerEntry> loadedEntries = xpLedgerPort.loadXpLedgerEntries(userId, page, size);
    boolean hasNext = loadedEntries.size() > size;
    List<XpLedgerEntry> pageEntries = hasNext ? loadedEntries.subList(0, size) : loadedEntries;
    List<XpLedgerEntryItem> entries = pageEntries.stream().map(this::mapToItem).toList();

    LocalDateTime now = ZonedDateTime.now(appZoneId).toLocalDateTime();
    LocalDate earnedOn = now.toLocalDate();
    Map<XpType, XpPolicy> policiesByType =
        policyPort.loadXpPolicies(now).stream()
            .collect(java.util.stream.Collectors.toMap(XpPolicy::getType, Function.identity()));

    List<XpDailyCapStatusItem> todayCaps =
        policiesByType.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> toTodayCap(userId, entry.getKey(), entry.getValue(), earnedOn))
            .toList();

    return GetMyXpLedgerResult.builder()
        .page(page)
        .size(size)
        .hasNext(hasNext)
        .earnedOn(earnedOn)
        .entries(entries)
        .todayCaps(todayCaps)
        .build();
  }

  private XpLedgerEntryItem mapToItem(XpLedgerEntry entry) {
    return XpLedgerEntryItem.builder()
        .xpLedgerId(entry.getId())
        .type(entry.getType())
        .xpAmount(entry.getXpAmount())
        .earnedOn(entry.getEarnedOn())
        .occurredAt(entry.getOccurredAt())
        .idempotencyKey(entry.getIdempotencyKey())
        .sourceRef(entry.getSourceRef())
        .createdAt(entry.getCreatedAt())
        .build();
  }

  private XpDailyCapStatusItem toTodayCap(
      Long userId, XpType type, XpPolicy policy, LocalDate earnedOn) {
    int dailyCap = policy.getDailyCap();
    int grantedCount = xpLedgerPort.countByUserIdAndTypeAndEarnedOn(userId, type, earnedOn);
    int remainingCount = dailyCap < 0 ? -1 : Math.max(0, dailyCap - grantedCount);
    return XpDailyCapStatusItem.builder()
        .type(type)
        .dailyCap(dailyCap)
        .grantedCount(grantedCount)
        .remainingCount(remainingCount)
        .build();
  }
}
