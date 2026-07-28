#Requires -Version 7.0
<#
.SYNOPSIS
    Capture repeatable memory baselines for CustoMIUIzer and target system processes.

.DESCRIPTION
    Uses ADB to collect dumpsys meminfo, /proc/<pid> status, smaps_rollup, fd count
    and thread count for one or more processes under a given scenario.
    Results are written as raw ADB output plus a parsed summary JSON to
    .devin/memory-audit/ by default.

.PARAMETER Targets
    Process names or package names to sample. Defaults to the CustoMIUIzer app,
    SystemUI, Launcher and system_server.

.PARAMETER Scenario
    A short name for the current measurement scenario, e.g. "T0_boot_1min".

.PARAMETER Samples
    How many consecutive samples to capture for each target.

.PARAMETER DelaySeconds
    Wait time between samples.

.PARAMETER OutDir
    Directory for raw output and summary JSON.

.PARAMETER Device
    ADB serial number. Leave empty to use the only connected device.

.EXAMPLE
    .\tools\capture-memory-baseline.ps1 -Scenario "after_locale_switch" -Samples 3 -DelaySeconds 10
#>

[CmdletBinding()]
param (
    [string[]]$Targets = @(
        "tv.withaibuild.customiuizer.r14",
        "com.android.systemui",
        "com.miui.home",
        "system_server"
    ),
    [string]$Scenario = "baseline",
    [int]$Samples = 3,
    [int]$DelaySeconds = 5,
    [string]$OutDir = ".devin/memory-audit",
    [string]$Device = ""
)

$ErrorActionPreference = "Stop"

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Error "adb not found in PATH."
}

$adbCmd = @("adb")
if ($Device) {
    $adbCmd += @("-s", $Device)
}

function Invoke-Adb($argsList) {
    $cmd = $adbCmd + $argsList
    $result = & $cmd[0] $cmd[1..($cmd.Length - 1)] 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "adb $argsList failed: $result"
        return $null
    }
    return $result
}

function Get-Pid($target) {
    $pidOut = Invoke-Adb @("shell", "pidof", $target)
    if ($pidOut) {
        return ($pidOut -split "\s+")[0].Trim()
    }
    return $null
}

function Get-ProcessNameForPackage($package) {
    # Some packages share a process (e.g. system_server has no package).
    # For packages, just use the package name; for system_server, keep it.
    return $package
}

function New-OutPath($target, $sample) {
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $safeTarget = $target -replace "[^a-zA-Z0-9._-]", "_"
    return "$OutDir/raw/$Scenario/$safeTarget/sample_$sample`_$ts"
}

function Parse-Meminfo($text) {
    $result = @{
        total_pss_kb     = $null
        total_rss_kb     = $null
        java_heap_kb     = $null
        native_heap_kb   = $null
        graphics_kb      = $null
        code_kb          = $null
        private_dirty_kb = $null
        dalvik_heap_kb   = $null
    }

    foreach ($line in $text -split "`n") {
        if ($line -match "TOTAL\s+:\s+([\d,]+)\s+([\d,]+)") {
            $result.total_pss_kb = [int]($matches[1] -replace ",", "")
            $result.total_rss_kb = [int]($matches[2] -replace ",", "")
        }
        elseif ($line -match "Java Heap:\s+([\d,]+)") {
            $result.java_heap_kb = [int]($matches[1] -replace ",", "")
        }
        elseif ($line -match "Native Heap:\s+([\d,]+)") {
            $result.native_heap_kb = [int]($matches[1] -replace ",", "")
        }
        elseif ($line -match "Graphics:\s+([\d,]+)") {
            $result.graphics_kb = [int]($matches[1] -replace ",", "")
        }
        elseif ($line -match "Code:\s+([\d,]+)") {
            $result.code_kb = [int]($matches[1] -replace ",", "")
        }
        elseif ($line -match "Private Dirty:\s+([\d,]+)") {
            $result.private_dirty_kb = [int]($matches[1] -replace ",", "")
        }
        elseif ($line -match "Dalvik Heap:\s+([\d,]+)") {
            $result.dalvik_heap_kb = [int]($matches[1] -replace ",", "")
        }
    }

    return $result
}

function Parse-Status($text) {
    $result = @{}
    foreach ($line in $text -split "`n") {
        if ($line -match "^(\w+):\s*(.+)$") {
            $result[$matches[1].ToLower()] = $matches[2].Trim()
        }
    }
    return $result
}

$summary = @{
    scenario      = $Scenario
    device_serial = if ($Device) { $Device } else { "default" }
    timestamp     = (Get-Date -Format "o")
    samples       = New-Object System.Collections.Generic.List[Object]
}

$bootCompleted = Invoke-Adb @("shell", "getprop", "sys.boot_completed")
Write-Host "Boot completed: $bootCompleted"

foreach ($target in $Targets) {
    $processName = Get-ProcessNameForPackage $target
    for ($i = 1; $i -le $Samples; $i++) {
        $pid = Get-Pid $target
        if (-not $pid) {
            Write-Warning "Could not find pid for $target, skipping"
            continue
        }

        Write-Host "Sampling $target (pid $pid) sample $i..."

        $outPath = New-OutPath $target $i
        New-Item -ItemType Directory -Path $outPath -Force | Out-Null

        $meminfo = Invoke-Adb @("shell", "dumpsys meminfo $pid")
        $status = Invoke-Adb @("shell", "cat /proc/$pid/status")
        $smaps = Invoke-Adb @("shell", "su -c 'cat /proc/$pid/smaps_rollup'")
        $fdCount = Invoke-Adb @("shell", "ls /proc/$pid/fd 2>/dev/null | wc -l")
        $threads = Invoke-Adb @("shell", "ps -T -p $pid")

        if ($meminfo) { $meminfo | Out-File -FilePath "$outPath/dumpsys_meminfo.txt" -Encoding UTF8 }
        if ($status) { $status | Out-File -FilePath "$outPath/status.txt" -Encoding UTF8 }
        if ($smaps) { $smaps | Out-File -FilePath "$outPath/smaps_rollup.txt" -Encoding UTF8 }
        if ($fdCount) { $fdCount | Out-File -FilePath "$outPath/fd_count.txt" -Encoding UTF8 }
        if ($threads) { $threads | Out-File -FilePath "$outPath/threads.txt" -Encoding UTF8 }

        $meminfoParsed = if ($meminfo) { Parse-Meminfo $meminfo } else { @{} }
        $statusParsed = if ($status) { Parse-Status $status } else { @{} }

        $sample = @{
            target        = $target
            process_name  = $processName
            sample        = $i
            pid           = $pid
            raw_dir       = $outPath
            meminfo       = $meminfoParsed
            status        = $statusParsed
            fd_count      = if ($fdCount -match "\d+") { [int]$matches[0] } else { $null }
            has_smaps     = [bool]$smaps
        }

        $summary.samples.Add($sample)

        if ($i -lt $Samples) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }
}

# Capture a snapshot of overall procstats.
$procstats = Invoke-Adb @("shell", "dumpsys procstats --hours 1")
if ($procstats) {
    New-Item -ItemType Directory -Path "$OutDir/raw/$Scenario" -Force | Out-Null
    $procstats | Out-File -FilePath "$OutDir/raw/$Scenario/procstats.txt" -Encoding UTF8
}

$summaryOut = "$OutDir/summary_$Scenario.json"
$summary | ConvertTo-Json -Depth 10 | Out-File -FilePath $summaryOut -Encoding UTF8
Write-Host "Summary written to $summaryOut"
