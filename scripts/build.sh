#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ "$#" -eq 0 ]]; then
    set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi

# WSL prefers a complete Linux toolchain. If it is absent, use an existing
# Windows toolchain without pretending Windows SDK executables run natively.
SDK_CANDIDATE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$PROJECT_ROOT/.tools/android-sdk}}"
if [[ "${TRAVEL_BUILD_PLATFORM:-}" == windows ]] || {
    [[ -r /proc/sys/kernel/osrelease ]] &&
    [[ "$(cat /proc/sys/kernel/osrelease)" == *[Mm]icrosoft* ]] &&
    { ! command -v java >/dev/null 2>&1 || [[ ! -x "$SDK_CANDIDATE/build-tools/35.0.0/aapt2" ]]; }
}; then
    if ! command -v powershell.exe >/dev/null 2>&1; then
        printf '%s\n' '未找到 Linux 工具链或 Windows PowerShell；参见 docs/构建环境.md。' >&2
        exit 1
    fi
    ps_quote() {
        local value="${1//\'/\'\'}"
        printf "'%s'" "$value"
    }
    PS_ARGUMENTS=""
    for argument in "$@"; do
        [[ -z "$PS_ARGUMENTS" ]] || PS_ARGUMENTS+=","
        PS_ARGUMENTS+="$(ps_quote "$argument")"
    done
    PS_SCRIPT='$ErrorActionPreference = "Stop"; $ProgressPreference = "SilentlyContinue"'
    PS_SCRIPT+=$'\n$OutputEncoding = [System.Text.UTF8Encoding]::new($false)\n[Console]::OutputEncoding = $OutputEncoding\n'
    PS_SCRIPT+="Set-Location -LiteralPath $(ps_quote "$(wslpath -w "$PROJECT_ROOT")")"
    PS_SCRIPT+=$'\n'
    PS_SCRIPT+="$(cat <<'POWERSHELL'
if ($env:TRAVEL_WINDOWS_JAVA_HOME) { $env:JAVA_HOME = $env:TRAVEL_WINDOWS_JAVA_HOME }
if (!(Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath "$($_.FullName)\bin\java.exe" } |
        Sort-Object Name -Descending | Select-Object -First 1
    if (!$jdk) { throw '找不到 Windows JDK；设置 TRAVEL_WINDOWS_JAVA_HOME。' }
    $env:JAVA_HOME = $jdk.FullName
}
if ($env:TRAVEL_WINDOWS_ANDROID_HOME) { $env:ANDROID_HOME = $env:TRAVEL_WINDOWS_ANDROID_HOME }
if (!(Test-Path -LiteralPath "$env:ANDROID_HOME\platforms\android-35\android.jar")) {
    if (Test-Path -LiteralPath 'E:\Android\Sdk\platforms\android-35\android.jar') {
        $env:ANDROID_HOME = 'E:\Android\Sdk'
    } else { throw '找不到 Windows Android SDK 35；设置 TRAVEL_WINDOWS_ANDROID_HOME。' }
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (!$env:GRADLE_USER_HOME -and (Test-Path -LiteralPath 'E:\Android\Gradle')) {
    $env:GRADLE_USER_HOME = 'E:\Android\Gradle'
}
[Console]::WriteLine('WSL 入口正在调用现有 Windows JDK / Android SDK。')
POWERSHELL
)"
    PS_SCRIPT+=$'\n'
    PS_SCRIPT+="& .\scripts\build-windows.ps1 -Tasks @($PS_ARGUMENTS)"
    PS_SCRIPT+=$'\nexit $LASTEXITCODE\n'
    ENCODED_SCRIPT="$(printf '%s' "$PS_SCRIPT" | iconv -f UTF-8 -t UTF-16LE | base64 -w0)"
    exec powershell.exe -NoLogo -NoProfile -NonInteractive -OutputFormat Text -EncodedCommand "$ENCODED_SCRIPT"
fi

if [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-17-openjdk-amd64 ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
    printf '%s\n' '需要 JDK 17 或兼容的 JDK；请参见 docs/构建环境.md。' >&2
    exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -d "$PROJECT_ROOT/.tools/android-sdk" ]]; then
    export ANDROID_HOME="$PROJECT_ROOT/.tools/android-sdk"
fi
if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi
if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties ]]; then
    printf '%s\n' '请设置 ANDROID_HOME，或按 docs/构建环境.md 安装项目内 Linux SDK。' >&2
    exit 1
fi
if [[ -n "${ANDROID_HOME:-}" ]]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

exec bash ./gradlew --no-daemon --console=plain "$@"
