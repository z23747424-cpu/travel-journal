param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'),
    [string]$BuildRoot = (Join-Path ([System.IO.Path]::GetTempPath()) 'TravelJournal-build')
)
$ErrorActionPreference = 'Stop'
function Get-SourceSha256([string]$Path) {
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    $stream = [System.IO.File]::OpenRead($Path)
    try { return ([System.BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$activeRoot = $projectRoot
$snapshot = $projectRoot -match '[^\x00-\x7F]'
$evidenceRoot = Join-Path $projectRoot '.tools/android-checks'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
if ($snapshot) {
    $resolvedBuildRoot = [System.IO.Path]::GetFullPath($BuildRoot)
    if ($resolvedBuildRoot -match '[^\x00-\x7F]') { throw 'BuildRoot must use ASCII characters for Windows Java argument files.' }
    $activeRoot = Join-Path $resolvedBuildRoot ('snapshot-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $activeRoot -Force | Out-Null
    foreach ($name in @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'gradle')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $activeRoot -Recurse
    }
    $activeApp = Join-Path $activeRoot 'app'
    New-Item -ItemType Directory -Path $activeApp -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'app/build.gradle.kts') -Destination $activeApp
    Copy-Item -LiteralPath (Join-Path $projectRoot 'app/src') -Destination $activeApp -Recurse
    $sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src') -File -Recurse)
    $sourceFiles += @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts') | ForEach-Object { Get-Item -LiteralPath (Join-Path $projectRoot $_) }
    $sourceFiles += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'gradle') -File -Recurse)
    $sourceManifest = foreach ($file in $sourceFiles) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1)
        $originalHash = Get-SourceSha256 $file.FullName
        $snapshotHash = Get-SourceSha256 (Join-Path $activeRoot $relative)
        if ($originalHash -ne $snapshotHash) { throw "Snapshot differs: $relative" }
        [PSCustomObject]@{ path = $relative; sha256 = $originalHash }
    }
    $sourceManifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceRoot 'source-manifest.json') -Encoding UTF8
    [Console]::WriteLine("Windows Java ASCII snapshot: $activeRoot")
    [Console]::WriteLine("Verified identical source files: $($sourceManifest.Count)")
}
Set-Content -LiteralPath (Join-Path $evidenceRoot 'last-build-root.txt') -Value $activeRoot -Encoding UTF8
if ($env:ANDROID_HOME) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
$buildExit = 1
Push-Location -LiteralPath $activeRoot
try {
    & (Join-Path $activeRoot 'gradlew.bat') @Tasks --no-daemon --console=plain
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
if ($snapshot) {
    foreach ($name in @('outputs', 'reports', 'test-results')) {
        $source = Join-Path $activeRoot "app/build/$name"
        if (Test-Path -LiteralPath $source) {
            $target = Join-Path $projectRoot "app/build/$name"
            New-Item -ItemType Directory -Path $target -Force | Out-Null
            Get-ChildItem -LiteralPath $source | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force }
        }
    }
}
exit $buildExit
