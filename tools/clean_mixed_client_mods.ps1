param(
    [Parameter(Mandatory=$true)][string]$Profile,
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string]$ArchiveReceipt,
    [Parameter(Mandatory=$true)][string]$RetiredDirectory,
    [Parameter(Mandatory=$true)][string]$Evidence
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-Child([string]$Path, [string]$Parent) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $prefix = [IO.Path]::GetFullPath($Parent).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Resolved path is outside the intended directory'
    }
    return $absolute
}
function Assert-Regular($Item) {
    if ($Item.PSIsContainer -or ($Item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Only ordinary direct files may be retired'
    }
}

$profilePath = (Resolve-Path -LiteralPath $Profile).Path
if ([IO.Path]::GetFileName($profilePath) -ne 'Naruto Complete Client - Friend Test') {
    throw 'This correction is restricted to the reviewed imported client profile'
}
$mods = Assert-Child (Join-Path $profilePath 'mods') $profilePath
$modsItem = Get-Item -LiteralPath $mods
if (-not $modsItem.PSIsContainer -or ($modsItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'Client mods must be an ordinary directory'
}
$retired = [IO.Path]::GetFullPath($RetiredDirectory)
if ((Test-Path -LiteralPath $retired) -or (Test-Path -LiteralPath $Evidence)) {
    throw 'Preserve existing retention folders and evidence'
}
if ($retired.StartsWith($profilePath.TrimEnd('\','/') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Retired JARs must remain outside the active client profile'
}
$receipt = Get-Content -LiteralPath $ArchiveReceipt -Raw | ConvertFrom-Json
if ($receipt.scope -ne 'SINGLE_CLIENT_MODS_FOLDER_REPLACEMENT' -or $receipt.mods -ne 58 -or
    -not $receipt.all_nested_jar_and_zip_crc_sha256_checks_passed -or
    (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $receipt.sha256) {
    throw 'Exact checked replacement archive required'
}
# Never terminate or modify a live owner game. Process arguments are not printed.
$normalizedProfile = $profilePath.Replace('\','/').ToLowerInvariant()
foreach ($process in Get-CimInstance Win32_Process) {
    if ($process.Name -in @('java.exe','javaw.exe') -and $process.CommandLine -and
        $process.CommandLine.Replace('\','/').ToLowerInvariant().Contains($normalizedProfile)) {
        throw 'Close the imported Minecraft client before cleaning its mods folder'
    }
}
$expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$zip = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($Archive))
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -notmatch '^mods/([^/\\:]+\.jar)$' -or -not $expected.Add($Matches[1])) {
            throw 'Only unique direct mod JARs may appear in the replacement'
        }
        $name = $Matches[1]
        $file = Assert-Child (Join-Path $mods $name) $mods
        Assert-Regular (Get-Item -LiteralPath $file)
        $stream = $entry.Open()
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
        finally { $stream.Dispose(); $algorithm.Dispose() }
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
            throw 'Install all current client JARs before removing mixed old files'
        }
    }
} finally { $zip.Dispose() }
if ($expected.Count -ne 58) { throw 'Exactly 58 complete replacement JARs required' }
$allowed = @('field-controls-1.5.0.jar','field-hud-0.18.0.jar',
    'Vale-Client-Lifecycle-Compat-0.1.2.jar','Naruto-Damage-Compat-0.1.0.jar',
    'Naruto-Residents-Server-Bridge-0.1.0.jar','Naruto-Server-Bridge-0.2.0.jar',
    'logo.jpeg','mcmod.info','pack.mcmeta')
$extras = @(Get-ChildItem -LiteralPath $mods -Force | Where-Object { -not $expected.Contains($_.Name) })
$plan = @()
foreach ($item in $extras) {
    Assert-Regular $item
    if ($item.Name -notin $allowed) { throw 'An unreviewed extra file needs inspection; no files moved' }
    $source = Assert-Child $item.FullName $mods
    $destination = Assert-Child (Join-Path $retired $item.Name) $retired
    $plan += [pscustomobject]@{ file=$item.Name; source=$source; destination=$destination;
        bytes=$item.Length; sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$result = [ordered]@{ schema=1; scope='ACTUAL_IMPORTED_CLIENT_MIXED_MODS_CLEANUP';
    status='APPLYING'; expected_current_jars=58; replacement_archive_sha256=$receipt.sha256;
    all_58_current_jar_hashes_matched_before_cleanup=$true; retired_files=@();
    resourcepacks_or_options_modified=$false; server_world_or_player_data_modified=$false;
    owner_game_process_stopped=$false; completed=$false }
$evidencePath = [IO.Path]::GetFullPath($Evidence)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($evidencePath)) | Out-Null
[IO.Directory]::CreateDirectory($retired) | Out-Null
function Save-Evidence { [IO.File]::WriteAllText($evidencePath, ($result | ConvertTo-Json -Depth 6) + "`n", [Text.UTF8Encoding]::new($false)) }
Save-Evidence
try {
    foreach ($row in $plan) {
        Move-Item -LiteralPath $row.source -Destination $row.destination
        if ((Test-Path -LiteralPath $row.source) -or
            (Get-FileHash -LiteralPath $row.destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $row.sha256) {
            throw 'Retired file verification failed'
        }
        $result.retired_files += [ordered]@{ file=$row.file; bytes=$row.bytes; sha256=$row.sha256 }
        Save-Evidence
    }
    $remaining = @(Get-ChildItem -LiteralPath $mods -Force)
    if ($remaining.Count -ne 58 -or @($remaining | Where-Object { -not $expected.Contains($_.Name) }).Count) {
        throw 'Final direct-file inventory differs from the checked client'
    }
    $result.status='PASS'; $result.completed=$true; Save-Evidence
    Write-Output 'PASS: 58 current client JARs; reviewed stale extras retained outside the active profile.'
} catch {
    $result.status='PARTIAL_OR_FAILED'; Save-Evidence
    throw
}
