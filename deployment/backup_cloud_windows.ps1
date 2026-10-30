[CmdletBinding()]
param(
    [ValidateSet('Install','Enable','Disable','Run','Status')][string]$Action='Status',
    [string]$ConfigPath,
    [string]$PythonPath,
    [string]$OutputDirectory=(Join-Path $env:LOCALAPPDATA 'BandiSuite\backups'),
    [ValidatePattern('^[A-Za-z0-9 ._-]{1,80}$')][string]$TaskName='Bandi Suite Cloud Backup'
)
$ErrorActionPreference='Stop'
function Quote([string]$value) {
    if ($value.Contains('"') -or $value.Contains("`r") -or $value.Contains("`n")) { throw 'Unsupported character in local path.' }
    return '"'+$value+'"'
}
if ($Action -eq 'Enable') { Enable-ScheduledTask -TaskName $TaskName | Out-Null; return }
if ($Action -eq 'Disable') { Disable-ScheduledTask -TaskName $TaskName | Out-Null; return }
if ($Action -eq 'Status') { Get-ScheduledTask -TaskName $TaskName; Get-ScheduledTaskInfo -TaskName $TaskName; return }
if (-not $ConfigPath) { throw 'Specify -ConfigPath pointing to the private JSON connection file.' }
$config=(Resolve-Path -LiteralPath $ConfigPath).Path
$python=if ($PythonPath) { (Resolve-Path -LiteralPath $PythonPath).Path } else { (Get-Command python.exe -ErrorAction Stop).Source }
$script=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'backup_cloud_schemas.py')).Path
$destination=[IO.Path]::GetFullPath($OutputDirectory)
if ($Action -eq 'Install') {
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) { throw 'Task already exists; inspect it before changing its configuration.' }
    $owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
    $arguments='-NoProfile -NonInteractive -WindowStyle Hidden -File '+(Quote $PSCommandPath)+' -Action Run -ConfigPath '+(Quote $config)+' -PythonPath '+(Quote $python)+' -OutputDirectory '+(Quote $destination)
    $execution=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments -WorkingDirectory $PSScriptRoot
    $daily=New-ScheduledTaskTrigger -Daily -At '03:30'
    $logon=New-ScheduledTaskTrigger -AtLogOn -User $owner
    $logon.Delay='PT3M'
    $principal=New-ScheduledTaskPrincipal -UserId $owner -LogonType Interactive -RunLevel Limited
    $settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 4) -StartWhenAvailable -RunOnlyIfNetworkAvailable -RestartCount 2 -RestartInterval (New-TimeSpan -Minutes 15) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $TaskName -Action $execution -Trigger @($daily,$logon) -Principal $principal -Settings $settings | Out-Null
    Disable-ScheduledTask -TaskName $TaskName | Out-Null
    Write-Output 'Daily and delayed-logon task installed DISABLED with missed-start recovery. Verify backup before enabling.'
    return
}
$arguments=(Quote $script)+' --config '+(Quote $config)+' --output-dir '+(Quote $destination)+' --skip-if-today'
$child=Start-Process -FilePath $python -ArgumentList $arguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
$child.WaitForExit()
exit $child.ExitCode
