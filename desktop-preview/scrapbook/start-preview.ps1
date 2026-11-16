param([switch]$NoOpen)
$ErrorActionPreference='Stop'
$port=8778
$url="http://127.0.0.1:$port"
try{$ready=(Invoke-RestMethod "$url/health" -TimeoutSec 1).app -eq 'lvye-province-scrapbook'}catch{$ready=$false}
if(!$ready){
    $scriptPath=Join-Path $PSScriptRoot 'server.py'
    $linuxPath=(& wsl.exe --distribution Ubuntu --exec wslpath -u ($scriptPath.Replace('\','/'))).Trim()
    if($LASTEXITCODE -ne 0){throw 'Ubuntu / WSL is unavailable.'}
    $logs=Join-Path $PSScriptRoot '../../.tools'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    Start-Process -FilePath 'wsl.exe' -ArgumentList @('--distribution','Ubuntu','--exec','python3','-u',$linuxPath,'--port',"$port") -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'scrapbook-preview.log') -RedirectStandardError (Join-Path $logs 'scrapbook-preview-error.log') | Out-Null
    for($attempt=0;$attempt -lt 30;$attempt++){
        Start-Sleep -Milliseconds 350
        try{if((Invoke-RestMethod "$url/health" -TimeoutSec 1).app -eq 'lvye-province-scrapbook'){$ready=$true;break}}catch{}
    }
}
if(!$ready){throw 'Preview did not start. Check .tools/scrapbook-preview-error.log; port 8778 may be in use.'}
if(!$NoOpen){Start-Process -FilePath "$url/?demo=1" | Out-Null}
Write-Output $url
