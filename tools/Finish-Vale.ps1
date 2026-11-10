# Creates an isolated candidate. Does not install software, start Minecraft,
# alter the selected source, change execution policy, or deploy to Bisect.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Plan,

    [string]$Destination = 'D:\Codex\Vale-Export',

    [string]$Name
)

$ErrorActionPreference = 'Stop'
$assembler = Join-Path $PSScriptRoot 'assemble_release.py'
foreach ($requiredTool in @('assemble_release.py', 'release_pack.py', 'package_bootstrap.py')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $requiredTool) -PathType Leaf)) {
        throw 'Keep Finish-Vale.ps1 and the Python tools from this checkout in the same tools folder.'
    }
}
$sourceItem = Get-Item -LiteralPath $Source
$planItem = Get-Item -LiteralPath $Plan
if ($planItem.PSIsContainer) {
    throw 'Plan must identify the reviewed JSON file, not a directory.'
}

$pythonCommand = Get-Command 'py.exe' -CommandType Application -ErrorAction SilentlyContinue
$pythonPrefix = @('-3')
if ($null -eq $pythonCommand) {
    $pythonCommand = Get-Command 'python.exe' -CommandType Application -ErrorAction SilentlyContinue
    $pythonPrefix = @()
}
if ($null -eq $pythonCommand) {
    throw 'Python 3.8 or later is required. No software was downloaded or installed.'
}

$arguments = $pythonPrefix + @(
    $assembler,
    '--source', $sourceItem.FullName,
    '--plan', $planItem.FullName,
    '--export-root', $Destination
)
if ($Name) {
    $arguments += @('--name', $Name)
}

Write-Host 'Creating a separate Vale candidate. Startup, world, client and visual acceptance remain pending.'
& $pythonCommand.Source @arguments
$assemblyExit = $LASTEXITCODE
if ($assemblyExit -ne 0) {
    [Console]::Error.WriteLine("Assembly stopped (exit $assemblyExit). Keep the original source and any incomplete output for inspection.")
    exit $assemblyExit
}
Write-Host 'Candidate bytes were verified. Check ASSEMBLY.json; this does not declare the pack ready for players.'
