param([switch]$PrepareOnly)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Stop-Activation([string]$Message) {
    [Console]::Error.WriteLine($Message)
    exit 1
}

try {
    # Every local path is derived from this script, independent of the shell cwd.
    $installerPath = Join-Path $PSScriptRoot 'install_portal_cron.py'
    $settingsPath = Join-Path $PSScriptRoot 'suite-public-settings.json'
    if (!(Test-Path -LiteralPath $installerPath -PathType Leaf) -or
        !(Test-Path -LiteralPath $settingsPath -PathType Leaf)) {
        Stop-Activation 'Installer o impostazioni pubbliche mancanti. Ripristina la cartella deployment.'
    }
    $settings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $gateway = [string]$settings.apiOrigins.bandiPortal
    $projectRef = [string]$settings.supabaseProjectRef
    $gatewayUri = $null
    if ($projectRef -cnotmatch '^[a-z]{20}$' -or
        ![Uri]::TryCreate($gateway, [UriKind]::Absolute, [ref]$gatewayUri) -or
        $gatewayUri.Scheme -cne 'https' -or $gatewayUri.UserInfo -or
        $gatewayUri.Query -or $gatewayUri.Fragment -or $gatewayUri.AbsolutePath -ne '/' -or
        $gatewayUri.Host -notmatch '^adf-bandi-portale-api-[a-z0-9-]+\.a\.run\.app$') {
        Stop-Activation 'Il gateway non corrisponde alle impostazioni pubbliche autorizzate.'
    }
    if (!$env:LOCALAPPDATA) { Stop-Activation 'Cartella locale utente non disponibile.' }
    $privateDirectory = Join-Path $env:LOCALAPPDATA "BandiSuite\private\release-$projectRef"
    $cronKeyPath = Join-Path $privateDirectory 'cron-key.local'
    $exportPath = Join-Path $privateDirectory 'portal-maintenance.json'
    $receiptPath = Join-Path $privateDirectory 'portal-maintenance.receipt.json'
    foreach ($privateFile in @($cronKeyPath, $exportPath)) {
        if (!(Test-Path -LiteralPath $privateFile -PathType Leaf) -or
            ((Get-Item -LiteralPath $privateFile).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            Stop-Activation 'Configurazione privata pronta non trovata o percorso non valido. Non inserire chiavi qui.'
        }
    }
    # Read the export in memory, never display headers or key values.
    $exported = Get-Content -LiteralPath $exportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $cronKey = [IO.File]::ReadAllText($cronKeyPath).Trim()
    $expectedUrl = $gateway.TrimEnd('/') + '/api/collaboration/v1/maintenance/heartbeat'
    if ([string]$exported.job.url -cne $expectedUrl -or
        [string]$exported.job.title -cne 'Bandi portal maintenance' -or
        $exported.job.enabled -ne $true -or
        [string]$exported.job.schedule.timezone -cne 'Europe/Rome' -or
        (@($exported.job.schedule.minutes) -join ',') -ne '0,30' -or
        $cronKey.Length -lt 32 -or $cronKey -cmatch '[^\x21-\x7e]' -or
        [string]$exported.job.extendedData.headers.'X-Cron-Key' -cne $cronKey) {
        Stop-Activation 'Il job privato non corrisponde al controllo periodico autorizzato.'
    }
    $python = Get-Command python.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (!$python) { Stop-Activation 'Python non trovato. Serve Python 3.10 o successivo.' }
    & $python.Source -I -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)'
    if ($LASTEXITCODE -ne 0) { Stop-Activation 'Serve Python 3.10 o successivo.' }
    if ($PrepareOnly) {
        Write-Host 'Preparazione OK: installer, gateway autorizzato e configurazione privata presenti.'
        Write-Host 'Nessun browser aperto, nessun prompt e nessuna chiamata API.'
        Write-Host "Ricevuta prevista: $receiptPath"
        exit 0
    }
    if ([Console]::IsInputRedirected) {
        Stop-Activation 'Esegui questo file in una finestra PowerShell locale interattiva.'
    }
    Write-Host 'Si apre il sito ufficiale cron-job.org nel browser normale.'
    Write-Host '1. Accedi al tuo account; se necessario crea un account gratuito.'
    Write-Host '2. In Settings / Impostazioni crea o copia una API key.'
    Write-Host '3. Torna qui e incollala nel prompt nascosto. Non inviarla in chat.'
    Write-Host 'La API key resta in memoria e non viene salvata nei file o negli argomenti.'
    Start-Process -FilePath 'https://console.cron-job.org/settings' | Out-Null
    $installerArguments = @(
        '-I', $installerPath,
        '--gateway-url', $gateway,
        '--cron-key-file', $cronKeyPath,
        '--export', $exportPath,
        '--receipt', $receiptPath,
        '--apply', '--prompt-api-key'
    )
    & $python.Source @installerArguments
    if ($LASTEXITCODE -ne 0) {
        Stop-Activation 'Attivazione non verificata. Leggi il messaggio sopra; nessun retry automatico.'
    }
    Write-Host 'Configurazione verificata su cron-job.org. Una nuova esecuzione conserva lo stesso job.'
    Write-Host 'Il primo heartbeat pianificato va controllato nel sito: questa ricevuta verifica la configurazione.'
    exit 0
} catch {
    # Do not print exceptions: parsing or HTTP diagnostics may include private data.
    Stop-Activation 'Preparazione interrotta. Verifica Python, impostazioni pubbliche e file privati.'
}
