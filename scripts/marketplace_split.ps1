<#
  marketplace_split.ps1
  Splits marketplace module into 4 submodules:
    classes     ← class management
    reservation ← reservation lifecycle
    store       ← trainer store
    sanction    ← trainer strike / ban
#>

$ErrorActionPreference = 'Stop'

$BASE = "c:\MZTK-BE\src\main\java\momzzangseven\mztkbe\modules\marketplace"
$TEST = "c:\MZTK-BE\src\test\java\momzzangseven\mztkbe\modules\marketplace"
$ROOT_PKG = "momzzangseven.mztkbe.modules.marketplace"

# ─────────────────────────────────────────────────────────────────
# 1. FILE → SUBMODULE mapping  (filename → submodule name)
# ─────────────────────────────────────────────────────────────────
$classesFiles = @(
  # api/controller
  "ClassController.java",
  # api/dto
  "GetClassDetailResponseDTO.java","GetClassesResponseDTO.java",
  "GetClassReservationInfoResponseDTO.java","GetTrainerClassesResponseDTO.java",
  "RegisterClassRequestDTO.java","RegisterClassResponseDTO.java",
  "ToggleClassStatusResponseDTO.java","UpdateClassRequestDTO.java","UpdateClassResponseDTO.java",
  # application/dto
  "ClassDetailInfo.java","ClassItem.java","ClassSlotInfo.java","ClassTimeCommand.java",
  "GetClassDetailQuery.java","GetClassDetailResult.java",
  "GetClassesQuery.java","GetClassesResult.java",
  "GetClassReservationInfoQuery.java","GetClassReservationInfoResult.java",
  "GetTrainerClassesQuery.java","GetTrainerClassesResult.java",
  "RegisterClassCommand.java","RegisterClassResult.java",
  "ReservationItem.java",
  "ToggleClassStatusCommand.java","ToggleClassStatusResult.java",
  "UpdateClassCommand.java","UpdateClassResult.java",
  "MarketplacePaginationConstants.java",
  # application/port/in
  "GetClassDetailUseCase.java","GetClassesUseCase.java",
  "GetClassReservationInfoUseCase.java","GetTrainerClassesUseCase.java",
  "RegisterClassUseCase.java","ToggleClassStatusUseCase.java","UpdateClassUseCase.java",
  # application/port/out
  "LoadClassImagesPort.java","LoadClassPort.java","LoadClassSlotPort.java","LoadClassTagPort.java",
  "LoadSlotReservationPort.java","LoadTrainerStorePort.java",
  "ManageClassTagPort.java","SaveClassPort.java","SaveClassSlotPort.java","UpdateClassImagesPort.java",
  # application/service
  "GetClassDetailService.java","GetClassesService.java","GetClassReservationInfoService.java",
  "GetTrainerClassesService.java","RegisterClassService.java",
  "ToggleClassStatusService.java","UpdateClassService.java",
  # domain/model
  "ClassSlot.java","MarketplaceClass.java",
  # domain/vo
  "ClassCategory.java",
  # infrastructure
  "ClassImageModuleAdapter.java","SlotReservationAdapter.java","TrainerStoreAdapter.java",
  "ClassPersistenceAdapter.java","ClassSlotPersistenceAdapter.java","ClassTagAdapter.java",
  "ClassSlotEntity.java","ClassTagEntity.java","MarketplaceClassEntity.java",
  "ClassSlotJpaRepository.java","ClassTagJpaRepository.java","MarketplaceClassJpaRepository.java"
)

$reservationFiles = @(
  # api/controller
  "ReservationUserController.java","ReservationTrainerController.java",
  # api/dto
  "ApproveReservationResponseDTO.java","CancelPendingReservationResponseDTO.java",
  "CompleteReservationResponseDTO.java","CreateReservationRequestDTO.java",
  "CreateReservationResponseDTO.java","RejectReservationRequestDTO.java","RejectReservationResponseDTO.java",
  # application/dto
  "ApproveReservationCommand.java","ApproveReservationResult.java",
  "CancelPendingReservationCommand.java","CancelPendingReservationResult.java",
  "CompleteReservationCommand.java","CompleteReservationResult.java",
  "CreateReservationCommand.java","CreateReservationResult.java",
  "RejectReservationCommand.java","RejectReservationResult.java",
  # application/port/in
  "ApproveReservationUseCase.java","AutoCancelReservationUseCase.java","AutoSettleReservationUseCase.java",
  "CancelPendingReservationUseCase.java","CompleteReservationUseCase.java",
  "CreateReservationUseCase.java","RejectReservationUseCase.java",
  # application/port/out
  "LoadReservationPort.java","SaveReservationPort.java","SubmitEscrowTransactionPort.java",
  # application/service
  "ApproveReservationService.java","AutoCancelBatchItemProcessor.java",
  "AutoCancelReservationService.java","AutoSettleBatchItemProcessor.java",
  "AutoSettleReservationService.java","CancelPendingReservationService.java",
  "CompleteReservationService.java","CreateReservationService.java",
  "RejectReservationService.java",
  # domain/model
  "Reservation.java",
  # domain/vo
  "ReservationStatus.java",
  # infrastructure
  "ReservationSanctionEventListener.java",
  "EscrowTransactionAdapter.java",
  "ReservationPersistenceAdapter.java",
  "ReservationEntity.java",
  "ReservationJpaRepository.java",
  "AutoCancelReservationScheduler.java","AutoSettleReservationScheduler.java"
)

$storeFiles = @(
  # api/controller
  "StoreController.java",
  # api/dto
  "GetStoreResponseDTO.java","UpsertStoreRequestDTO.java","UpsertStoreResponseDTO.java",
  # application/dto
  "GetStoreCommand.java","GetStoreResult.java","UpsertStoreCommand.java","UpsertStoreResult.java",
  # application/port/in
  "GetStoreUseCase.java","UpsertStoreUseCase.java",
  # application/port/out
  "LoadStorePort.java","SaveStorePort.java",
  # application/service
  "GetStoreService.java","UpsertStoreService.java",
  # domain/model
  "TrainerStore.java",
  # infrastructure
  "StorePersistenceAdapter.java","TrainerStoreEntity.java","TrainerStoreJpaRepository.java"
)

$sanctionFiles = @(
  # domain/vo
  "TrainerBannedEvent.java","TrainerStrikeEvent.java",
  # application/dto
  "RecordTrainerStrikeCommand.java",
  # application/port/in
  "RecordTrainerStrikeUseCase.java",
  # application/port/out
  "LoadTrainerSanctionPort.java","ManageTrainerSanctionPort.java",
  # application/service
  "RecordTrainerStrikeService.java",
  # infrastructure
  "SanctionCheckAdapter.java","SanctionManageAdapter.java"
)

# ─────────────────────────────────────────────────────────────────
# 2. Build a lookup: filename → submodule
# ─────────────────────────────────────────────────────────────────
$fileToSub = @{}
foreach ($f in $classesFiles)    { $fileToSub[$f] = "classes" }
foreach ($f in $reservationFiles){ $fileToSub[$f] = "reservation" }
foreach ($f in $storeFiles)      { $fileToSub[$f] = "store" }
foreach ($f in $sanctionFiles)   { $fileToSub[$f] = "sanction" }

# ─────────────────────────────────────────────────────────────────
# 3. Walk all marketplace .java files, copy to new location,
#    updating the package declaration in each file.
# ─────────────────────────────────────────────────────────────────
$moved = @{}   # old-abs-path → new-abs-path

Get-ChildItem -Recurse $BASE -Filter "*.java" | ForEach-Object {
    $file = $_
    $sub  = $fileToSub[$file.Name]
    if (-not $sub) {
        Write-Warning "UNMAPPED: $($file.Name)"
        return
    }

    # Relative path from marketplace root, e.g. "application\service\Foo.java"
    $rel = $file.FullName.Substring($BASE.Length + 1)

    # New destination path
    $newPath = Join-Path $BASE "$sub\$rel"
    $newDir  = Split-Path $newPath -Parent
    if (-not (Test-Path $newDir)) { New-Item -ItemType Directory -Force -Path $newDir | Out-Null }

    # Read content, fix package declaration
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    # Replace: package momzzangseven.mztkbe.modules.marketplace.XXX
    $content = $content -replace `
        "^(package $($ROOT_PKG.Replace('.','\.')))(;|\.)",
        "`$1.$sub`$2"

    Set-Content -Path $newPath -Value $content -Encoding UTF8 -NoNewline
    $moved[$file.FullName] = $newPath
    Write-Host "MOVED: $($file.Name) → $sub\$rel"
}

Write-Host "`n✅ Copied $($moved.Count) files"

# ─────────────────────────────────────────────────────────────────
# 4. Build import replacement table
#    Every type has a unique class name, so we replace by class name.
#    Pattern: import <ROOT_PKG>.<layer>.<ClassName>
#    → import <ROOT_PKG>.<sub>.<layer>.<ClassName>
# ─────────────────────────────────────────────────────────────────
# We'll rewrite all .java files in the project with updated imports.

$allProjectJava = Get-ChildItem -Recurse "c:\MZTK-BE\src" -Filter "*.java"

foreach ($projFile in $allProjectJava) {
    $content = Get-Content $projFile.FullName -Raw -Encoding UTF8
    $original = $content

    foreach ($fname in $fileToSub.Keys) {
        $className = [System.IO.Path]::GetFileNameWithoutExtension($fname)
        $sub = $fileToSub[$fname]
        # Match: import momzzangseven.mztkbe.modules.marketplace.<anything>.<ClassName>;
        # but NOT already containing the submodule
        $pattern = "($($ROOT_PKG.Replace('.','\.')))\.((?!classes\.|reservation\.|store\.|sanction\.)[^;]+\.$className);"
        $replacement = "`$1.$sub.`$2;"
        $content = $content -replace $pattern, $replacement
    }

    if ($content -ne $original) {
        Set-Content -Path $projFile.FullName -Value $content -Encoding UTF8 -NoNewline
        Write-Host "IMPORTS UPDATED: $($projFile.Name)"
    }
}

Write-Host "`n✅ Import update pass complete"

# ─────────────────────────────────────────────────────────────────
# 5. Delete original files (now replaced by copies)
# ─────────────────────────────────────────────────────────────────
foreach ($oldPath in $moved.Keys) {
    Remove-Item $oldPath -Force
    Write-Host "DELETED: $(Split-Path $oldPath -Leaf)"
}

# Clean up empty directories
Get-ChildItem -Recurse $BASE -Directory | Sort-Object FullName -Descending | ForEach-Object {
    if ((Get-ChildItem $_.FullName -Force).Count -eq 0) {
        Remove-Item $_.FullName -Force
        Write-Host "RMDIR: $($_.FullName)"
    }
}

Write-Host "`n🎉 Marketplace submodule split complete!"
