[CmdletBinding()]
param(
    [string]$KotlinHome = "",
    [string]$AndroidSdk = "",
    [string]$JavaHome = "",
    [string]$GradleCache = "",
    [switch]$DownloadMissing
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$checkRoot = Join-Path $projectRoot ".tools/core-check"
$libraryRoot = Join-Path $checkRoot "libs"
New-Item -ItemType Directory -Force -Path $libraryRoot | Out-Null

function Find-ExistingPath {
    param([string[]]$Candidates)
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

if (-not $KotlinHome) {
    $KotlinHome = Find-ExistingPath @(
        $env:KOTLIN_HOME,
        "E:/Android/Android Studio/plugins/Kotlin/kotlinc",
        "$env:ProgramFiles/Android/Android Studio/plugins/Kotlin/kotlinc",
        "$env:LOCALAPPDATA/Programs/Android Studio/plugins/Kotlin/kotlinc"
    )
}
if (-not $KotlinHome -or -not (Test-Path -LiteralPath (Join-Path $KotlinHome "lib/kotlin-compiler.jar"))) {
    throw "Kotlin compiler not found. Supply -KotlinHome <Android Studio/plugins/Kotlin/kotlinc>."
}
$KotlinHome = (Resolve-Path -LiteralPath $KotlinHome).Path
$compilerLib = Join-Path $KotlinHome "lib"

if (-not $AndroidSdk) {
    $AndroidSdk = Find-ExistingPath @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "E:/Android/Sdk", "$env:LOCALAPPDATA/Android/Sdk")
}
if (-not $AndroidSdk) { throw "Android SDK not found. Supply -AndroidSdk <SDK directory>." }
$androidJar = Join-Path $AndroidSdk "platforms/android-35/android.jar"
if (-not (Test-Path -LiteralPath $androidJar)) { throw "Android SDK platform 35 is required: $androidJar" }
$androidJar = (Resolve-Path -LiteralPath $androidJar).Path

if ($JavaHome) {
    $java = Find-ExistingPath @((Join-Path $JavaHome "bin/java.exe"))
} else {
    $javaCandidates = @()
    if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME "bin/java.exe" }
    $javaCandidates += [System.IO.Path]::GetFullPath((Join-Path $KotlinHome "../../../jbr/bin/java.exe"))
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $javaCandidates += $javaCommand.Source }
    $java = Find-ExistingPath $javaCandidates
}
if (-not $java) { throw "Java 17 or newer is required. Supply -JavaHome <JDK directory>." }

$cacheCandidates = @($GradleCache, $env:GRADLE_USER_HOME, "E:/Android/Gradle", "$env:USERPROFILE/.gradle")
$moduleRoots = @($cacheCandidates | Where-Object { $_ } | ForEach-Object { Join-Path $_ "caches/modules-2/files-2.1" } | Select-Object -Unique)

function Find-Library {
    param([string]$Group, [string]$Artifact, [string]$Version, [string]$Bundled = "")
    $fileName = "$Artifact-$Version.jar"
    foreach ($moduleRoot in $moduleRoots) {
        $versionRoot = Join-Path $moduleRoot "$Group/$Artifact/$Version"
        if (Test-Path -LiteralPath $versionRoot) {
            $found = Get-ChildItem -LiteralPath $versionRoot -Recurse -File -Filter $fileName | Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }
    $localJar = Join-Path $libraryRoot $fileName
    if ((Test-Path -LiteralPath $localJar) -and (Get-Item -LiteralPath $localJar).Length -gt 0) { return $localJar }
    if ($Bundled -and (Test-Path -LiteralPath $Bundled)) { return $Bundled }
    if (-not $DownloadMissing) {
        throw "Missing $fileName in Gradle/project caches. Re-run with -DownloadMissing to fetch the pinned Maven Central dependency."
    }
    $groupPath = $Group.Replace('.', '/')
    $url = "https://repo.maven.apache.org/maven2/$groupPath/$Artifact/$Version/$fileName"
    $temporaryJar = "$localJar.part"
    Write-Host "Downloading $fileName from Maven Central..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $temporaryJar -UseBasicParsing -TimeoutSec 120
        $archive = [System.IO.Compression.ZipFile]::OpenRead($temporaryJar)
        $archive.Dispose()
        Move-Item -LiteralPath $temporaryJar -Destination $localJar -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryJar) { Remove-Item -LiteralPath $temporaryJar }
    }
    return $localJar
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$exifJar = Join-Path $libraryRoot 'exifinterface-1.3.7.jar'
if (-not (Test-Path -LiteralPath $exifJar)) {
    $exifAar = $null
    foreach ($moduleRoot in $moduleRoots) {
        $exifRoot = Join-Path $moduleRoot 'androidx.exifinterface/exifinterface/1.3.7'
        if (Test-Path -LiteralPath $exifRoot) {
            $found = Get-ChildItem -LiteralPath $exifRoot -Recurse -File -Filter 'exifinterface-1.3.7.aar' | Select-Object -First 1
            if ($found) { $exifAar = $found.FullName; break }
        }
    }
    if (-not $exifAar) {
        if (-not $DownloadMissing) { throw 'Missing AndroidX ExifInterface 1.3.7. Build once or use -DownloadMissing.' }
        $exifAar = Join-Path $libraryRoot 'exifinterface-1.3.7.aar'
        Invoke-WebRequest -Uri 'https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.3.7/exifinterface-1.3.7.aar' -OutFile $exifAar -UseBasicParsing -TimeoutSec 120
    }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($exifAar)
    try {
        $entry = $archive.GetEntry('classes.jar')
        if (-not $entry) { throw 'Invalid ExifInterface AAR: classes.jar is missing.' }
        $inputStream = $entry.Open()
        $outputStream = [System.IO.File]::Create($exifJar)
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
    } finally { $archive.Dispose() }
}
$junit = Find-Library "junit" "junit" "4.13.2"
$hamcrest = Find-Library "org.hamcrest" "hamcrest-core" "1.3"
$json = Find-Library "org.json" "json" "20240303"
$coroutines = Find-Library "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" "1.9.0" (Join-Path $compilerLib "kotlinx-coroutines-core-jvm.jar")
$stdlib = Join-Path $compilerLib "kotlin-stdlib.jar"
if (-not (Test-Path -LiteralPath $stdlib)) { throw "Kotlin standard library missing: $stdlib" }

# Real org.json must precede android.jar, whose org.json classes throw Stub!.
$classpath = @($stdlib, $coroutines, $junit, $hamcrest, $json, $exifJar, $androidJar) -join [System.IO.Path]::PathSeparator
$sourceRoot = Join-Path $projectRoot "app/src/main/java/cn/edu/traveljournal"
$testRoot = Join-Path $projectRoot "app/src/test/java/cn/edu/traveljournal"
$sources = @("Models.kt", "JournalRepository.kt", "Backup.kt", "AiClient.kt", "Search.kt") | ForEach-Object { Join-Path $sourceRoot $_ }
$tests = @("CoreLogicTest.kt", "AiWorkflowTest.kt") | ForEach-Object { Join-Path $testRoot $_ }
foreach ($file in @($sources) + @($tests)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required source file missing: $file" }
}

# A fresh output directory prevents a stale removed test class from affecting results.
$classes = Join-Path $checkRoot ("classes-" + [Guid]::NewGuid().ToString("N"))
$compileLog = Join-Path $checkRoot "compile.log"
$testLog = Join-Path $checkRoot "jvm-tests.log"
$environmentLog = Join-Path $checkRoot "environment.log"
@("Java: $java", "Kotlin: $KotlinHome", "Android: $androidJar", "JUnit: $junit", "Hamcrest: $hamcrest", "JSON: $json", "Coroutines: $coroutines") | Set-Content -LiteralPath $environmentLog -Encoding UTF8
& $java -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version 2>&1 | ForEach-Object { Write-Host $_; $_ | Add-Content -LiteralPath $environmentLog -Encoding UTF8 }
if ($LASTEXITCODE -ne 0) { throw "Cannot run Kotlin compiler. See $environmentLog" }
Write-Host "Compiling five production core sources and the two formal JVM test classes..."
& $java -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -jvm-target 17 -classpath $classpath -d $classes @sources @tests *> $compileLog
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $compileLog; throw "Core compilation failed. See $compileLog" }
& $java -cp "$classes$([System.IO.Path]::PathSeparator)$classpath" org.junit.runner.JUnitCore cn.edu.traveljournal.CoreLogicTest cn.edu.traveljournal.AiWorkflowTest *> $testLog
$testExit = $LASTEXITCODE
Get-Content -LiteralPath $testLog
if ($testExit -ne 0) { throw "Core JVM tests failed. See $testLog" }
Write-Host "Formal JVM checks passed. Logs: $checkRoot"
Write-Host "This excludes Robolectric, the Gradle build, actual Android runtime, Keystore and real provider calls."
