param([switch]$NoOpen)
$ErrorActionPreference='Stop'
$scriptFile=Join-Path $PSScriptRoot 'server.py'
$serverPath=(& wsl.exe --distribution Ubuntu --exec wslpath -u ($scriptFile.Replace('\','/'))).Trim()
if($LASTEXITCODE -ne 0){throw 'Ubuntu/WSL was not available.'}
$previewPort=$null
for($candidate=8766;$candidate -le 8776;$candidate++){
    try{
        $health=Invoke-RestMethod "http://127.0.0.1:$candidate/health" -TimeoutSec 1
        if($health.app -eq 'travel-journal-desktop-preview'){$previewPort=$candidate;break}
    }catch{}
    $listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$candidate)
    try{$listener.Start();$listener.Stop();$previewPort=$candidate;break}catch{try{$listener.Stop()}catch{}}
}
if(!$previewPort){throw 'No free preview port in 8766-8776.'}
$url="http://127.0.0.1:$previewPort"
try{$ready=(Invoke-RestMethod "$url/health" -TimeoutSec 1).app -eq 'travel-journal-desktop-preview'}catch{$ready=$false}
if(!$ready){
    $logs=Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    Start-Process -FilePath 'wsl.exe' -ArgumentList @('--distribution','Ubuntu','--exec','python3','-u',$serverPath,'--port',"$previewPort") -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'desktop-preview.log') -RedirectStandardError (Join-Path $logs 'desktop-preview-error.log') | Out-Null
    for($attempt=0;$attempt -lt 30;$attempt++){
        Start-Sleep -Milliseconds 400
        try{if((Invoke-RestMethod "$url/health" -TimeoutSec 1).app -eq 'travel-journal-desktop-preview'){$ready=$true;break}}catch{}
    }
}
if(!$ready){throw 'Local preview did not become ready. See .tools/desktop-preview-error.log.'}
if(!$NoOpen){Start-Process -FilePath "$url/?demo=1" | Out-Null}
Write-Output $url
