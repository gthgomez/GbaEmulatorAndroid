<#
.SYNOPSIS
    Verifies 16 KB APK zip alignment and ELF LOAD segment alignment for GbaEmulatorAndroid.

.PARAMETER ApkPath
    Path to a built APK (defaults to debug output).

.PARAMETER ExpectedJniCount
    Expected JNI export count in libgbaemulator.so (default 31).

.EXAMPLE
    pwsh -File scripts/verify-16kb-alignment.ps1
    pwsh -File scripts/verify-16kb-alignment.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk
#>
param(
    [string]$ApkPath = "",
    [int]$ExpectedJniCount = 31
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $RepoRoot "app/build/outputs/apk/debug/app-debug.apk"
}

$ResolvedApk = Convert-Path $ApkPath -ErrorAction SilentlyContinue
if (-not $ResolvedApk -or -not (Test-Path $ResolvedApk -PathType Leaf)) {
    Write-Error "APK not found: $ApkPath"
    exit 1
}

function Resolve-AndroidTool {
    param(
        [string]$Name,
        [string[]]$SdkRelativePaths
    )

    $FromPath = Get-Command $Name -ErrorAction SilentlyContinue
    if ($FromPath) {
        return $FromPath.Source
    }

    $SdkPath = $env:ANDROID_HOME
    if (-not $SdkPath) {
        $SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }

    foreach ($Relative in $SdkRelativePaths) {
        $Candidate = Join-Path $SdkPath $Relative
        if (Test-Path $Candidate) {
            return $Candidate
        }
    }

    return $null
}

Write-Host "16 KB alignment check: $ResolvedApk" -ForegroundColor Cyan

$ZipAlign = Resolve-AndroidTool -Name "zipalign" -SdkRelativePaths @(
    "build-tools\36.0.0\zipalign.exe",
    "build-tools\35.0.0\zipalign.exe"
)
if (-not $ZipAlign) {
    Write-Error "zipalign not found in PATH or Android SDK build-tools"
    exit 1
}

& $ZipAlign -c -v -P 16 4 $ResolvedApk | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Error "zipalign 16 KB check failed"
    exit 1
}
Write-Host "PASS: zipalign -c -P 16" -ForegroundColor Green

Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip = [System.IO.Compression.ZipFile]::OpenRead($ResolvedApk)
$SoEntries = @($Zip.Entries | Where-Object { $_.FullName -like "lib/*/*.so" })

if ($SoEntries.Count -eq 0) {
    $Zip.Dispose()
    Write-Error "No native .so libraries found in APK"
    exit 1
}

$ReadObj = Resolve-AndroidTool -Name "llvm-readobj" -SdkRelativePaths @(
    "ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readobj.exe",
    "ndk\27.1.12297006\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readobj.exe"
)
$Nm = Resolve-AndroidTool -Name "llvm-nm" -SdkRelativePaths @(
    "ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-nm.exe",
    "ndk\27.1.12297006\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-nm.exe"
)

$TempDir = Join-Path $env:TEMP "gba-16kb-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
New-Item -ItemType Directory -Force -Path $TempDir | Out-Null
$Failed = $false

try {
    foreach ($Entry in $SoEntries) {
        $Extracted = Join-Path $TempDir ($Entry.Name)
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, $Extracted, $true)
        Write-Host "`n$($Entry.FullName)" -ForegroundColor Yellow

        if ($ReadObj) {
            $ProgramHeaders = & $ReadObj --program-headers $Extracted 2>&1
            $LoadAlignments = New-Object System.Collections.Generic.List[string]
            $InLoad = $false
            foreach ($Line in $ProgramHeaders) {
                $Text = [string]$Line
                if ($Text -match "Type:\s+PT_LOAD") {
                    $InLoad = $true
                    continue
                }
                if ($InLoad -and $Text -match "Alignment:\s+(0x[0-9A-Fa-f]+|\d+)") {
                    $LoadAlignments.Add($Matches[1])
                    $InLoad = $false
                }
            }
            if ($LoadAlignments.Count -eq 0) {
                Write-Host "  WARN: no PT_LOAD alignments parsed" -ForegroundColor Yellow
                $Failed = $true
            } else {
                foreach ($Align in $LoadAlignments) {
                    $AlignValue = if ($Align.StartsWith("0x")) {
                        [Convert]::ToInt64($Align, 16)
                    } else {
                        [int64]$Align
                    }
                    if ($AlignValue -lt 16384) {
                        Write-Host "  FAIL: PT_LOAD Alignment $Align (< 16384)" -ForegroundColor Red
                        $Failed = $true
                    } else {
                        Write-Host "  PASS: PT_LOAD Alignment $Align" -ForegroundColor Green
                    }
                }
            }
        } else {
            Write-Host "  WARN: llvm-readobj not found; skipped ELF LOAD check" -ForegroundColor Yellow
        }

        if ($Entry.Name -eq "libgbaemulator.so" -and $Nm) {
            $JniCount = @(
                & $Nm -D $Extracted 2>&1 |
                    Select-String "Java_" |
                    Where-Object { $_.Line -match "\sT\s" -or $_.Line -match "\sW\s" }
            ).Count
            Write-Host "  JNI exports: $JniCount (expected $ExpectedJniCount)" -ForegroundColor $(if ($JniCount -eq $ExpectedJniCount) { "Green" } else { "Red" })
            if ($JniCount -ne $ExpectedJniCount) {
                $Failed = $true
            }
        }
    }
} finally {
    if (Test-Path $TempDir) {
        try {
            Remove-Item -Recurse -Force $TempDir -ErrorAction Stop
        } catch {
            Write-Host "  NOTE: temp cleanup skipped ($TempDir)" -ForegroundColor DarkGray
        }
    }
    $Zip.Dispose()
}

if ($Failed) {
    Write-Error "16 KB alignment or JNI export verification failed"
    exit 1
}

Write-Host "`nAll checks passed." -ForegroundColor Green
