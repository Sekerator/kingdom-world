<#
.SYNOPSIS
    Smoke test of KingdomWorld on a real dedicated Forge server.

.DESCRIPTION
    Installs a Forge 1.20.1-47.4.10 dedicated server outside the repository, puts the freshly built mod jar
    into it, accepts the EULA and runs two server sessions on a fresh world:

      1. start, wait for "Done", found the kingdom SmokeTest with a throne at 8 70 8
         ("kingdomworld admin found"), claim chunk [2, 0] for it ("kingdomworld admin claim"), add three
         citizens ("kingdomworld admin citizens spawn"), list them, let one die ("kingdomworld admin citizens
         kill") and check that two remain and none has a body without players, then "stop";
      2. start again, check with "kingdomworld debug list", "kingdomworld admin owner" and "kingdom info" that
         the kingdom, its id and all 10 chunks survived the restart, and with "kingdomworld admin citizens list"
         and "info" that exactly the same two citizens are there (nothing lost, nothing doubled), then "stop".

    The test passes when both sessions start and stop cleanly, the kingdom, its land and its citizens created in
    session 1 are found unchanged in session 2, the fire protection hook is active, and the logs contain no errors or
    exceptions related to kingdomworld. The JVM
    class loading log is also checked to prove that no class of the mod's client package is loaded on the
    dedicated server.

    With -Pack the server lives in "server-pack" and additionally gets the server-side and common mods of the
    modpack instance (client-only mods are skipped). The modpack folder is only read, never modified.

    Exit code 0 means success; any failure ends the script with exit code 1.

.PARAMETER Pack
    Also install the modpack's server-side mods.

.PARAMETER WithoutMod
    Diagnostic mode: do not install KingdomWorld, only check that the server reaches "Done" and stops.
    Useful with -Pack to find out whether a pack mod breaks the server on its own.

.PARAMETER SkipBuild
    Use the jar already in build/libs instead of running "gradlew build".

.PARAMETER TestEnvRoot
    Folder for the test servers and the installer cache. Must be outside the repository.

.PARAMETER PackModsDir
    Mods folder of the modpack instance (read only).

.PARAMETER ExcludeModIds
    Additional mod ids to leave out in -Pack mode.

.PARAMETER JavaHome
    JDK 17 used for the build and the server.

.PARAMETER Memory
    Maximum heap of the server JVM, e.g. 2G or 6G.

.PARAMETER MapModsDir
    Folder with jars of server-side map mods (BlueMap, Dynmap) to install as well; other jars there are skipped. The
    test then also checks that every installed map mod shows both kingdoms and the caravan, after the first start and
    again after the restart (by the marker lines of the mod's log, and for BlueMap by its live markers over HTTP).
    BlueMap starts only after the download of the game's resources is accepted, so the script accepts it in the
    server's BlueMap config. Nothing of this folder is modified.

.PARAMETER Port
    Server port. By default 25599, or 25598 with -Pack. Give each copy of the repository its own port (and its
    own -TestEnvRoot) when several smoke tests run on one machine at the same time.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack -SkipBuild
#>
[CmdletBinding()]
param(
    [switch]$Pack,
    [switch]$WithoutMod,
    [switch]$SkipBuild,
    [string]$TestEnvRoot = 'D:\Projects\kingdom-world-testenv',
    [string]$PackModsDir = 'D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods',
    [string[]]$ExcludeModIds = @(),
    [string]$JavaHome = $(if ($env:KINGDOMWORLD_JAVA_HOME) { $env:KINGDOMWORLD_JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot' }),
    [string]$Memory = '',
    [ValidateRange(0, 65535)]
    [int]$Port = 0,
    [string]$MapModsDir = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ForgeVersion = '1.20.1-47.4.10'
$InstallerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$ForgeVersion/forge-$ForgeVersion-installer.jar"
$KingdomName = 'SmokeTest'
$PartnerName = 'SmokePartner'
# Where the kingdom of the world of the smoke check appears (the server looks for flat land around it), and how long it may take.
$NpcX = -700
$NpcZ = -700
# What the rulers of the kingdoms of the world remembered in session3, checked again after the restart in session4.
$script:RulerTurns = 0
$script:RulerDecrees = 0

# Mods that only make sense on a client. Mods that declare clientSideOnly=true are skipped as well.
$ClientOnlyModIds = @(
    'absoluteafkcinema', 'ambientsounds', 'clientsort', 'eatinganimation', 'embeddium', 'rubidium',
    'entity_model_features', 'entity_texture_features', 'entityculling', 'holdmyitems', 'immediatelyfast',
    'irisflw', 'locatorbar', 'mousetweaks', 'notenoughanimations', 'oculus', 'sound_physics_remastered'
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Java = Join-Path $JavaHome 'bin\java.exe'
$ServerDir = Join-Path $TestEnvRoot $(if ($Pack) { 'server-pack' } else { 'server-clean' })
$CacheDir = Join-Path $TestEnvRoot 'cache'
$SmokeLogDir = Join-Path $ServerDir 'smoke-logs'
if (-not $Memory) { $Memory = if ($Pack) { '6G' } else { '2G' } }
$StartTimeout = if ($Pack) { 1500 } elseif ($MapModsDir) { 900 } else { 420 }
$StopTimeout = if ($Pack) { 600 } else { 180 }
$CommandTimeout = 90
$NpcTimeout = if ($Pack) { 1200 } else { 600 }
# Mod ids of the server-side map mods taken from -MapModsDir, and the time they may take to show markers.
$ServerMapModIds = @('bluemap', 'dynmap')
$MapTimeout = 240
$script:MapMods = @()

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:Warnings = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host "    OK: $Message" -ForegroundColor Green }
function Add-Failure([string]$Message) { $script:Failures.Add($Message); Write-Host "    FAIL: $Message" -ForegroundColor Red }
function Add-Warning([string]$Message) { $script:Warnings.Add($Message); Write-Host "    WARN: $Message" -ForegroundColor Yellow }

function Stop-Smoke([string]$Message) {
    if ($Message) { Add-Failure $Message }
    Write-Summary
    exit 1
}

function Write-Summary {
    Write-Host ''
    Write-Host '==== Smoke test summary ====' -ForegroundColor Cyan
    Write-Host "Server folder: $ServerDir"
    Write-Host "Logs: $SmokeLogDir"
    foreach ($w in $script:Warnings) { Write-Host "  warning: $w" -ForegroundColor Yellow }
    if (Get-Variable -Name RulerLoad -Scope Script -ErrorAction SilentlyContinue) { Write-Host "Turns of the rulers: $($script:RulerLoad)" }
    if (Get-Variable -Name WarLoad -Scope Script -ErrorAction SilentlyContinue) { Write-Host "Wars: $($script:WarLoad)" }
    if ($script:Failures.Count -eq 0) {
        Write-Host 'RESULT: PASSED' -ForegroundColor Green
    } else {
        foreach ($f in $script:Failures) { Write-Host "  failure: $f" -ForegroundColor Red }
        Write-Host 'RESULT: FAILED' -ForegroundColor Red
    }
}

function Read-SharedText([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return '' }
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete)
    try {
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    } finally {
        $stream.Dispose()
    }
}

function Set-ServerProperty([string]$File, [string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path -LiteralPath $File) { $lines = @(Get-Content -LiteralPath $File) }
    $found = $false
    $lines = @($lines | ForEach-Object {
        if ($_ -match "^\s*$([regex]::Escape($Key))\s*=") { $found = $true; "$Key=$Value" } else { $_ }
    })
    if (-not $found) { $lines += "$Key=$Value" }
    Set-Content -LiteralPath $File -Value $lines -Encoding ASCII
}

function Get-ModInfo([string]$Jar) {
    # Returns mod ids declared in [[mods]] sections and whether the jar marks itself client-only.
    $ids = New-Object System.Collections.Generic.List[string]
    $clientOnly = $false
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry = $zip.GetEntry('META-INF/mods.toml')
        if ($entry) {
            $reader = New-Object System.IO.StreamReader($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $inMods = $false
            foreach ($line in ($text -split "`r?`n")) {
                if ($line -match '^\s*\[\[mods\]\]') { $inMods = $true; continue }
                if ($line -match '^\s*\[') { $inMods = $false; continue }
                if ($line -match '^\s*clientSideOnly\s*=\s*true') { $clientOnly = $true }
                if ($inMods -and $line -match '^\s*modId\s*=\s*"([^"]+)"') { $ids.Add($Matches[1]) }
            }
        }
    } finally {
        $zip.Dispose()
    }
    return [pscustomobject]@{ Ids = $ids.ToArray(); ClientOnly = $clientOnly }
}

function Invoke-Build {
    Write-Step 'Building the mod (gradlew build)'
    $env:JAVA_HOME = $JavaHome
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') build --console=plain
        if ($LASTEXITCODE -ne 0) { Stop-Smoke "gradlew build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Get-ModJar {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'build\libs') -Filter 'kingdomworld-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|slim|all)\.jar$' } |
        Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) { Stop-Smoke 'No kingdomworld jar in build/libs; run without -SkipBuild' }
    return $jars[0].FullName
}

function Install-ForgeServer {
    $argsFile = Join-Path $ServerDir "libraries\net\minecraftforge\forge\$ForgeVersion\win_args.txt"
    if (Test-Path -LiteralPath $argsFile) {
        Write-Ok "Forge $ForgeVersion server already installed"
        return
    }
    Write-Step "Installing Forge $ForgeVersion server into $ServerDir"
    New-Item -ItemType Directory -Force -Path $ServerDir, $CacheDir | Out-Null
    $installer = Join-Path $CacheDir "forge-$ForgeVersion-installer.jar"
    if (-not (Test-Path -LiteralPath $installer)) {
        Write-Host "    downloading $InstallerUrl"
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $InstallerUrl -OutFile $installer -UseBasicParsing
    }
    $installLog = Join-Path $CacheDir "install-$(Split-Path -Leaf $ServerDir).log"
    $process = Start-Process -FilePath $Java -ArgumentList @('-jar', "`"$installer`"", '--installServer', "`"$ServerDir`"") `
        -WorkingDirectory $CacheDir -NoNewWindow -Wait -PassThru -RedirectStandardOutput $installLog -RedirectStandardError "$installLog.err"
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $argsFile)) {
        Stop-Smoke "Forge installer failed (exit code $($process.ExitCode)), see $installLog"
    }
    Write-Ok 'Forge server installed'
}

function Initialize-Server([string]$ModJar) {
    Write-Step 'Preparing server folder'
    Set-Content -LiteralPath (Join-Path $ServerDir 'eula.txt') -Value 'eula=true' -Encoding ASCII
    $properties = Join-Path $ServerDir 'server.properties'
    # A port other than the default avoids clashes with a server that may already run on this machine.
    Set-ServerProperty $properties 'server-port' $(if ($Port -gt 0) { "$Port" } elseif ($Pack) { '25598' } else { '25599' })
    Set-ServerProperty $properties 'motd' 'KingdomWorld smoke test'

    # Accidents far from players are random; the smoke test switches them off so that its miner always lives.
    $defaults = Join-Path $ServerDir 'defaultconfigs'
    New-Item -ItemType Directory -Force -Path $defaults | Out-Null
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $serverDefaults = "[work]`n`ttaskRiskMultiplier = 0.0`n"
    # Roads: a city next to the capital, a long way searched quickly, and no camps or growth by chance.
    # A small wall around the city: a ring of radius 4 takes at most 128 logs and keeps off the building site of the shed.
    $serverDefaults += "[roads]`n`tminCityDistance = 16`n`tcampSpawnChance = 0.0`n`tcampGrowthChance = 0.0`n`twallRadius = 4`n"
    # Slow caravans: the caravan of the deal is still on the road when the server restarts.
    $serverDefaults += "[trade]`n`tcaravanBlocksPerMinute = 20.0`n"
    # The life of cities: nobody falls ill by chance and fairs open only when the smoke test opens one.
    $serverDefaults += "[life]`n`tsickChance = 0`n`tfairEveryDays = 0`n"
    # With map mods the web maps are updated every second, so that the checks do not wait for the default 5 s.
    if ($MapModsDir) { $serverDefaults += "[maps]`n`twebMapUpdateSeconds = 1`n" }
    [System.IO.File]::WriteAllText((Join-Path $defaults 'kingdomworld-server.toml'), $serverDefaults, $utf8)

    foreach ($dir in @('world', 'logs', 'crash-reports', 'smoke-logs')) {
        $path = Join-Path $ServerDir $dir
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
    New-Item -ItemType Directory -Force -Path $SmokeLogDir | Out-Null

    # The data pack of the smoke check: a building template that needs Create. A new world turns it on by itself.
    $datapacks = Join-Path $ServerDir 'world\datapacks\kingdomworld-smoke'
    New-Item -ItemType Directory -Force -Path $datapacks | Out-Null
    Copy-Item -Path (Join-Path $RepoRoot 'scripts\smoke-datapack\*') -Destination $datapacks -Recurse -Force

    $modsDir = Join-Path $ServerDir 'mods'
    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null

    if (-not $WithoutMod) {
        Copy-Item -LiteralPath $ModJar -Destination $modsDir
        Write-Ok "installed $(Split-Path -Leaf $ModJar)"
    } else {
        Add-Warning 'KingdomWorld is NOT installed (-WithoutMod diagnostic run)'
    }

    if ($Pack) {
        if (-not (Test-Path -LiteralPath $PackModsDir)) { Stop-Smoke "Modpack folder not found: $PackModsDir" }
        $excluded = @($ClientOnlyModIds + $ExcludeModIds)
        $copied = 0
        foreach ($jar in @(Get-ChildItem -LiteralPath $PackModsDir -Filter '*.jar' | Sort-Object Name)) {
            $info = Get-ModInfo $jar.FullName
            $hit = @($info.Ids | Where-Object { $excluded -contains $_ })
            if ($info.ClientOnly -or $hit.Count -gt 0) {
                $reason = if ($hit.Count -gt 0) { $hit -join ',' } else { 'clientSideOnly=true' }
                Write-Host "    skip  $($jar.Name) ($reason)"
                continue
            }
            Copy-Item -LiteralPath $jar.FullName -Destination $modsDir
            $copied++
        }
        Write-Ok "copied $copied modpack jar(s) from $PackModsDir"
    }

    if ($MapModsDir) {
        if (-not (Test-Path -LiteralPath $MapModsDir)) { Stop-Smoke "Map mods folder not found: $MapModsDir" }
        foreach ($jar in @(Get-ChildItem -LiteralPath $MapModsDir -Filter '*.jar' | Sort-Object Name)) {
            $ids = @((Get-ModInfo $jar.FullName).Ids | Where-Object { $ServerMapModIds -contains $_ })
            if ($ids.Count -eq 0) {
                Write-Host "    skip  $($jar.Name) (not a server map mod)"
                continue
            }
            Copy-Item -LiteralPath $jar.FullName -Destination $modsDir
            $script:MapMods += $ids
            Write-Ok "installed map mod $($jar.Name)"
        }
        if ($script:MapMods -contains 'bluemap') {
            # BlueMap only starts once the download of the game's resources is accepted in its core config.
            $blueMapConfig = Join-Path (Join-Path $ServerDir 'config') 'bluemap'
            New-Item -ItemType Directory -Force -Path $blueMapConfig | Out-Null
            $core = Join-Path $blueMapConfig 'core.conf'
            $utf8 = New-Object System.Text.UTF8Encoding($false)
            if (Test-Path -LiteralPath $core) {
                $text = [System.IO.File]::ReadAllText($core) -replace 'accept-download\s*:\s*false', 'accept-download: true'
                [System.IO.File]::WriteAllText($core, $text, $utf8)
            } else {
                [System.IO.File]::WriteAllText($core, "accept-download: true`nrender-thread-count: 1`n", $utf8)
            }
        }
    }
}

function Start-Server([string]$Phase) {
    $logsDir = Join-Path $ServerDir 'logs'
    if (Test-Path -LiteralPath $logsDir) { Remove-Item -LiteralPath $logsDir -Recurse -Force }
    $classLog = "smoke-logs/classload-$Phase.log"
    $arguments = @(
        "-Xmx$Memory",
        "-Xlog:class+load=info:file=$classLog",
        '@user_jvm_args.txt',
        "@libraries/net/minecraftforge/forge/$ForgeVersion/win_args.txt",
        'nogui'
    ) -join ' '
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Java
    $startInfo.Arguments = $arguments
    $startInfo.WorkingDirectory = $ServerDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    # Windows PowerShell creates the stdin writer with the console input encoding and flushes it right away.
    # Under code page 65001 that encoding carries a byte order mark, which would reach the server in front of
    # the first command ("?kingdomworld ..."). Switch to UTF-8 without a BOM while the process starts.
    $savedInputEncoding = $null
    try {
        if ([Console]::InputEncoding.GetPreamble().Length -gt 0) {
            $savedInputEncoding = [Console]::InputEncoding
            [Console]::InputEncoding = $script:Utf8NoBom
        }
    } catch {
        Add-Warning "could not change the console input encoding: $($_.Exception.Message)"
    }
    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
    } finally {
        if ($savedInputEncoding) { try { [Console]::InputEncoding = $savedInputEncoding } catch { } }
    }
    # Commands go through our own writer: UTF-8 without a BOM, flushed after every line.
    $stdin = New-Object System.IO.StreamWriter($process.StandardInput.BaseStream, $script:Utf8NoBom)
    $stdin.AutoFlush = $true
    $stdin.NewLine = "`n"
    # Drain the console streams so the server never blocks on a full pipe; latest.log is used for checks.
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    return [pscustomobject]@{ Process = $process; Stdin = $stdin; Stdout = $stdout; Stderr = $stderr; Phase = $Phase; Started = Get-Date }
}

function Get-LatestLog { Read-SharedText (Join-Path $ServerDir 'logs\latest.log') }

function Wait-ForLog([object]$Server, [string]$Pattern, [int]$TimeoutSeconds, [string]$FailPattern = '') {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $log = Get-LatestLog
        $match = [regex]::Match($log, $Pattern)
        if ($match.Success) { return $match }
        if ($FailPattern -and [regex]::IsMatch($log, $FailPattern)) { return $null }
        if ($Server.Process.HasExited) { return $null }
        Start-Sleep -Milliseconds 1000
    }
    return $null
}

function Count-Log([string]$Pattern) { [regex]::Matches((Get-LatestLog), $Pattern).Count }

# Waits for a match after the first $Seen ones, for answers to commands that are sent more than once.
function Wait-ForNewLog([object]$Server, [string]$Pattern, [int]$TimeoutSeconds, [int]$Seen) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $found = [regex]::Matches((Get-LatestLog), $Pattern)
        if ($found.Count -gt $Seen) { return $found[$Seen] }
        if ($Server.Process.HasExited) { return $null }
        Start-Sleep -Milliseconds 1000
    }
    return $null
}

function Send-ServerCommand([object]$Server, [string]$Command) {
    Write-Host "    > $Command"
    $Server.Stdin.WriteLine($Command)
}

function Stop-Server([object]$Server) {
    if (-not $Server.Process.HasExited) {
        Send-ServerCommand $Server 'stop'
        if (-not $Server.Process.WaitForExit($StopTimeout * 1000)) {
            Add-Failure "[$($Server.Phase)] server did not stop within $StopTimeout s, killing it"
            $Server.Process.Kill()
            $Server.Process.WaitForExit()
        }
    }
    $Server.Process.WaitForExit()
    $exitCode = $Server.Process.ExitCode
    Save-PhaseLogs $Server
    if ($exitCode -ne 0) {
        Add-Failure "[$($Server.Phase)] server exited with code $exitCode"
    } else {
        Write-Ok "[$($Server.Phase)] server stopped (exit code 0)"
    }
}

function Save-PhaseLogs([object]$Server) {
    $phase = $Server.Phase
    try { Set-Content -LiteralPath (Join-Path $SmokeLogDir "$phase-stdout.log") -Value $Server.Stdout.Result -Encoding UTF8 } catch { }
    try { Set-Content -LiteralPath (Join-Path $SmokeLogDir "$phase-stderr.log") -Value $Server.Stderr.Result -Encoding UTF8 } catch { }
    foreach ($name in @('latest.log', 'debug.log')) {
        $source = Join-Path $ServerDir "logs\$name"
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $SmokeLogDir "$phase-$name") -Force
        }
    }
}

function Test-PhaseLogs([string]$Phase) {
    # Errors and exceptions that mention the mod fail the test; its warnings are reported.
    $problems = New-Object System.Collections.Generic.List[string]
    foreach ($name in @('latest.log', 'debug.log', 'stdout.log', 'stderr.log')) {
        $file = Join-Path $SmokeLogDir "$Phase-$name"
        if (-not (Test-Path -LiteralPath $file)) { continue }
        foreach ($line in (Get-Content -LiteralPath $file -Encoding UTF8)) {
            if ($line -notmatch '(?i)kingdomworld|sekerator') { continue }
            $isLogRecord = $line -match '^\[[^\]]*\] \[[^\]]*/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]'
            # Errors: ERROR/FATAL records, frames of our code in a stack trace, exception lines that are part of
            # a stack trace (they do not start with a log record header) and warnings that carry an exception.
            $isError = $line -match '/(ERROR|FATAL)\]' `
                -or $line -match '^\s+at io\.github\.sekerator\.kingdomworld\.' `
                -or (-not $isLogRecord -and $line -match '(?i)(exception|error|caused by)') `
                -or ($line -match '/WARN\]' -and $line -match '(?i)exception')
            if ($isError) {
                $problems.Add("${name}: $($line.Trim())")
            } elseif ($line -match '/WARN\] \[io\.gi\.se\.ki|/WARN\] \[io\.github\.sekerator') {
                Add-Warning "[$Phase] $($line.Trim())"
            }
        }
    }
    if ($problems.Count -gt 0) {
        $shown = @($problems | Select-Object -Unique | Select-Object -First 20)
        Add-Failure "[$Phase] kingdomworld errors in logs:`n      $($shown -join "`n      ")"
    } else {
        Write-Ok "[$Phase] no kingdomworld errors or exceptions in logs"
    }

    $crashDir = Join-Path $ServerDir 'crash-reports'
    if (Test-Path -LiteralPath $crashDir) {
        $reports = @(Get-ChildItem -LiteralPath $crashDir -File)
        if ($reports.Count -gt 0) { Add-Failure "[$Phase] crash report(s) written: $($reports.Name -join ', ')" }
    }

    if (-not $WithoutMod) {
        $classLog = Join-Path $SmokeLogDir "classload-$Phase.log"
        $classText = Read-SharedText $classLog
        if (-not $classText) {
            Add-Failure "[$Phase] class loading log is missing"
        } elseif ($classText -notmatch 'io\.github\.sekerator\.kingdomworld\.KingdomWorld ') {
            Add-Failure "[$Phase] class loading log does not show the mod entry point, the check is not reliable"
        } else {
            $clientClasses = @([regex]::Matches($classText, 'io\.github\.sekerator\.kingdomworld\.client\.[\w.$]+') | ForEach-Object { $_.Value } | Select-Object -Unique)
            if ($clientClasses.Count -gt 0) {
                Add-Failure "[$Phase] client classes were loaded on the dedicated server: $($clientClasses -join ', ')"
            } else {
                Write-Ok "[$Phase] no client classes of the mod were loaded"
            }
        }
    }
}

# Checks that every installed map mod shows both kingdoms and the caravan.
function Test-MapMarkers([object]$Server, [string]$Phase) {
    foreach ($mod in @(@{ Id = 'bluemap'; Name = 'BlueMap' }, @{ Id = 'dynmap'; Name = 'Dynmap' })) {
        if ($script:MapMods -notcontains $mod.Id) { continue }
        $pattern = "KingdomWorld $($mod.Name) markers: 2 kingdom\(s\) shown, 1 caravan\(s\), (\d+) marker\(s\)"
        if (Wait-ForLog $Server $pattern $MapTimeout) {
            Write-Ok "[$Phase] $($mod.Name) shows both kingdoms and the caravan"
        } else {
            Add-Failure "[$Phase] $($mod.Name) did not report markers for 2 kingdoms and 1 caravan within $MapTimeout s"
        }
    }
    if ($script:MapMods -contains 'bluemap') {
        # BlueMap's web server hands out the live markers of the overworld map.
        $client = New-Object System.Net.WebClient
        $client.Proxy = [System.Net.GlobalProxySelection]::GetEmptyWebProxy()
        $client.Encoding = [System.Text.Encoding]::UTF8
        try {
            $json = $client.DownloadString('http://127.0.0.1:8100/maps/world/live/markers.json')
            if ($json -match 'kingdomworld-kingdoms' -and $json -match $KingdomName -and $json -match $PartnerName -and $json -match 'kingdomworld-caravans') {
                Write-Ok "[$Phase] BlueMap serves the markers of both kingdoms and the caravan"
            } else {
                Add-Failure "[$Phase] BlueMap's live markers lack the kingdoms or the caravan"
            }
        } catch {
            Add-Warning "[$Phase] BlueMap's web server did not answer: $($_.Exception.Message)"
        } finally {
            $client.Dispose()
        }
    }
}

function Invoke-Phase([string]$Phase, [scriptblock]$Body) {
    Write-Step "Session '$Phase': starting server"
    $server = Start-Server $Phase
    $done = Wait-ForLog $server '\]: Done \((\d+[.,]\d+)s\)!' $StartTimeout
    if (-not $done) {
        if (-not $server.Process.HasExited) { $server.Process.Kill(); $server.Process.WaitForExit() }
        Save-PhaseLogs $server
        Test-PhaseLogs $Phase
        Stop-Smoke "[$Phase] server did not reach 'Done' within $StartTimeout s (exited: $($server.Process.HasExited))"
    }
    Write-Ok "[$Phase] server started in $($done.Groups[1].Value)s"
    try {
        & $Body $server
    } finally {
        Stop-Server $server
        Test-PhaseLogs $Phase
    }
}

# ---------------------------------------------------------------------------------------------------------

Write-Host "KingdomWorld smoke test ($(if ($Pack) { 'modpack' } else { 'clean' }) server)" -ForegroundColor Cyan
if (-not (Test-Path -LiteralPath $Java)) { Stop-Smoke "Java not found at $Java (set -JavaHome)" }
$repoFull = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
if ([System.IO.Path]::GetFullPath($TestEnvRoot).StartsWith($repoFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    Stop-Smoke "TestEnvRoot must be outside the repository: $TestEnvRoot"
}

$modJar = $null
if (-not $WithoutMod) {
    if (-not $SkipBuild) { Invoke-Build }
    $modJar = Get-ModJar
}
Install-ForgeServer
Initialize-Server $modJar

if ($WithoutMod) {
    Invoke-Phase 'baseline' { param($server) }
    Write-Summary
    if ($script:Failures.Count -gt 0) { exit 1 }
    exit 0
}

$script:CreatedId = $null
$script:ConstructionId = $null
$script:HutId = $null
$script:HutTemplate = $null
$script:HutInfo = $null
$script:ConstructionInfo = $null
$script:BuilderId = $null
$script:CitizenIds = @()
$script:GuildSquad = $null
$script:ArmySquad = $null
$script:ArmyBand = $null
$script:ArmyBandSquad = $null
$script:ArmyPaidUntil = $null
$script:ArmyWeapons = @()
$script:ArmySoldiers = 0
$script:RoadId = $null
$script:WallId = $null
$script:WallRow = $null
$script:RoadPaid = $null
$script:RoadUnits = $null
$script:CaravanWay = $null
$script:GuildHunt = $null
$script:GuildBounty = $null
$script:GuildMoney = -1
# Companions and parties: owners who never log in, so that nothing of theirs may change while they are away.
$script:CompanionOwner = '00000000-0000-4000-8000-00000000c0de'
$script:CompanionMember = '00000000-0000-4000-8000-00000000c0df'
$script:CompanionEntry = $null
$script:CompanionMoney = -1
$script:CompanionWeapon = $null
# The law: players who never log in, so that their records, goods and debts may only change by the law.
$script:LawThief = '00000000-0000-4000-8000-00000000a001'
$script:LawWanted = '00000000-0000-4000-8000-00000000a002'
$script:LawOutlaw = '00000000-0000-4000-8000-00000000a003'
$script:LawConfiscation = $null
$script:LawGoods = $null
$script:LawDebt = -1
$script:LawReputation = $null
$script:LawMoney = -1
# The life of cities: the tavern, its innkeeper, an ill citizen and the money after the festival.
$script:TavernId = $null
$script:InnkeeperId = $null
$script:SickId = $null
$script:EconomyRaw = $null
$script:TradeMoney = $null
$script:MinerId = $null
$script:NpcName = $null
$script:WarId = $null
$script:WarScore = $null
$script:WarNumbers = $null
$script:Mined = 0
$ConsoleFailPattern = 'Unknown or incomplete command|Incorrect argument|already exists|There is no kingdom|read-only'
$IdPattern ='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'

# Lists the citizens of the smoke kingdom and returns their short ids, or $null if the list did not appear.
function Get-CitizenList([object]$Server, [int]$Expected, [string]$Phase) {
    $before = (Get-LatestLog).Length
    Send-ServerCommand $Server "kingdomworld admin citizens list $KingdomName"
    $header = Wait-ForLog $Server "Citizens of ${KingdomName}: $Expected \((\d+) present in the world\)" $CommandTimeout $ConsoleFailPattern
    if (-not $header) {
        Add-Failure "[$Phase] 'kingdomworld admin citizens list' did not report $Expected citizen(s)"
        return $null
    }
    if ($header.Groups[1].Value -ne '0') {
        Add-Failure "[$Phase] $($header.Groups[1].Value) citizen bodies exist on a server without players"
    }
    # The entries follow the header; give the server a moment to print them all.
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $log = (Get-LatestLog).Substring($before)
        $ids = @([regex]::Matches($log, '(?m) - ([0-9a-f]{8}) \S+: ') | ForEach-Object { $_.Groups[1].Value })
        if ($ids.Count -ge $Expected) { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    if ($ids.Count -ne $Expected) {
        Add-Failure "[$Phase] expected $Expected citizen entries, found $($ids.Count)"
        return $null
    }
    if (@($ids | Select-Object -Unique).Count -ne $ids.Count) {
        Add-Failure "[$Phase] a citizen is listed twice: $($ids -join ', ')"
    }
    return $ids
}

# The money of a kingdom's currency as 'admin trade rates' counts it (escrow of deals and of the guild included), or -1.
function Get-KingdomMoney([object]$Server, [string]$Kingdom) {
    $ratePattern = '(\S+): rate (\d+), money (\d+), coins in hands (\d+), treasury (\d+), reserves (\d+), exported today (\d+)'
    $seen = Count-Log $ratePattern
    Send-ServerCommand $Server 'kingdomworld admin trade rates'
    [void](Wait-ForNewLog $Server $ratePattern $CommandTimeout $seen)
    Start-Sleep -Seconds 1
    $found = [regex]::Matches((Get-LatestLog), "${Kingdom}: rate \d+, money (\d+),")
    if ($found.Count -eq 0) { return -1 }
    return [int64]$found[$found.Count - 1].Groups[1].Value
}

# Crystals of monsters in a kingdom's storehouses and their energy, as 'admin guild energy' tells them, or $null.
function Get-KingdomCrystals([object]$Server, [string]$Kingdom) {
    $pattern = "Crystals of ${Kingdom}: (\d+), energy (\d+)"
    $seen = Count-Log $pattern
    Send-ServerCommand $Server "kingdomworld admin guild energy $Kingdom"
    return Wait-ForNewLog $Server $pattern $CommandTimeout $seen
}

# The magic of a kingdom as 'admin magic energy' tells it (energy, capacity, crystals in the storehouses and their energy,
# research done and under way, spells, devices), or $null.
function Get-KingdomMagic([object]$Server, [string]$Kingdom) {
    $pattern = "Magic of ${Kingdom}: energy (\d+) of (\d+), crystals (\d+) with (\d+) energy, research done (\S+), under way (\S+), spells (\S+), devices: ([^\r\n]+)"
    $seen = Count-Log $pattern
    Send-ServerCommand $Server "kingdomworld admin magic energy $Kingdom"
    return Wait-ForNewLog $Server $pattern $CommandTimeout $seen
}

Invoke-Phase 'session1' {
    param($server)
    if (-not (Wait-ForLog $server 'KingdomWorld loaded 0 kingdom\(s\)' 10)) {
        Add-Failure '[session1] expected a fresh world with 0 kingdoms'
    }
    if (Wait-ForLog $server 'KingdomWorld fire protection: active' 10) {
        Write-Ok '[session1] fire protection hook is active'
    } else {
        Add-Failure '[session1] the fire protection hook did not report itself active'
    }
    Send-ServerCommand $server "kingdomworld admin found $KingdomName 8 70 8"
    $created = Wait-ForLog $server "Founded kingdom $KingdomName \(id ($IdPattern)\) with (\d+) chunk\(s\)" $CommandTimeout $ConsoleFailPattern
    if ($created) {
        $script:CreatedId = $created.Groups[1].Value
        if ($created.Groups[2].Value -eq '9') {
            Write-Ok "[session1] kingdom founded with id $($script:CreatedId) and 9 chunks"
        } else {
            Add-Failure "[session1] expected 9 initial chunks, got $($created.Groups[2].Value)"
        }
    } else {
        Add-Failure "[session1] 'kingdomworld admin found $KingdomName' did not report success"
    }
    Send-ServerCommand $server "kingdomworld admin claim $KingdomName 2 0"
    if (Wait-ForLog $server "Chunk \[2, 0\] now belongs to $KingdomName" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] chunk [2, 0] claimed'
    } else {
        Add-Failure "[session1] 'kingdomworld admin claim $KingdomName 2 0' did not report success"
    }
    Send-ServerCommand $server 'kingdomworld admin claim SmokeTest 9 9'
    if (Wait-ForLog $server 'Chunk \[9, 9\] cannot be claimed' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] a chunk that does not touch the land is refused'
    } else {
        Add-Failure '[session1] claiming a detached chunk was not refused'
    }

    Send-ServerCommand $server "kingdomworld admin citizens spawn 3 $KingdomName"
    if (Wait-ForLog $server "Spawned 3 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] 3 citizens added'
    } else {
        Add-Failure "[session1] 'kingdomworld admin citizens spawn 3 $KingdomName' did not report success"
    }
    $ids = Get-CitizenList $server 3 'session1'
    if ($ids) {
        Write-Ok "[session1] citizens listed: $($ids -join ', '), no bodies without players"
        $victim = $ids[0]
        Send-ServerCommand $server "kingdomworld admin citizens kill $victim"
        if (Wait-ForLog $server "Citizen \S+ \($victim\) is dead" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] citizen $victim died by command"
        } else {
            Add-Failure "[session1] 'kingdomworld admin citizens kill $victim' did not report the death"
        }
        $left = Get-CitizenList $server 2 'session1'
        if ($left) {
            if ($left -contains $victim) { Add-Failure "[session1] the dead citizen $victim is still listed" }
            $script:CitizenIds = @($left)
            Write-Ok "[session1] 2 citizens remain: $($left -join ', ')"
        }
    }

    # Work posts: a mine with a miner far from players, who digs by the estimate of its zone.
    if ($script:CitizenIds.Count -gt 0) {
        $script:MinerId = $script:CitizenIds[0]
        # The mine lies in chunks that stay loaded without players, and in solid rock whatever the world looks like.
        Send-ServerCommand $server 'forceload add 0 0 31 31'
        if (-not (Wait-ForLog $server 'Marked .* chunks in minecraft:overworld from \[0, 0\] to \[1, 1\]' $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the chunks of the mine could not be force loaded'
        }
        Start-Sleep -Seconds 2
        Send-ServerCommand $server 'fill 4 36 4 20 41 20 minecraft:stone'
        Send-ServerCommand $server 'setblock 2 70 2 kingdomworld:storehouse'
        if (-not (Wait-ForLog $server 'Changed the block at 2, 70, 2' $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the storehouse could not be placed'
        }
        Send-ServerCommand $server "kingdomworld admin storage put minecraft:iron_pickaxe 2 $KingdomName"
        if (Wait-ForLog $server "Stored 2 of minecraft:iron_pickaxe in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] pickaxes put into the storehouse'
        } else {
            Add-Failure '[session1] pickaxes could not be put into the storehouse'
        }
        Send-ServerCommand $server 'setblock 12 40 12 kingdomworld:work_post'
        Send-ServerCommand $server 'kingdomworld admin posts set 12 40 12 profession miner'
        if (Wait-ForLog $server 'Work post at 12 40 12: Miner, radius \d+, seats 1' $CommandTimeout 'No work post at|Unknown or incomplete command') {
            Write-Ok '[session1] work post placed and set to mining'
        } else {
            Add-Failure '[session1] the work post was not registered or could not be set to mining'
        }
        Send-ServerCommand $server "kingdomworld admin citizens profession $($script:MinerId) miner"
        if (Wait-ForLog $server '\S+ is now: Miner' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] citizen $($script:MinerId) became a miner"
        } else {
            Add-Failure "[session1] citizen $($script:MinerId) could not become a miner"
        }
        Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 20"
        if (-not (Wait-ForLog $server "Ran 20 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the simulation steps did not run'
        }
        Send-ServerCommand $server "kingdomworld admin posts list $KingdomName"
        $post = Wait-ForLog $server ' - 12 40 12: Miner, workers 1/1, radius \d+, (.+), today (\d+), about (\d+) a day' $CommandTimeout $ConsoleFailPattern
        if (-not $post) {
            Add-Failure '[session1] the work post is not listed with its miner'
        } elseif ([int]$post.Groups[2].Value -le 0) {
            Add-Failure "[session1] the miner gathered nothing far from players (status: $($post.Groups[1].Value))"
        } else {
            $script:Mined = [int]$post.Groups[2].Value
            Write-Ok "[session1] the miner gathered $($script:Mined) item(s) far from players ($($post.Groups[1].Value))"
        }
        Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
        $stored = Wait-ForLog $server "Storehouses of ${KingdomName}: (\d+) kinds of items in 1 storehouses" $CommandTimeout $ConsoleFailPattern
        if ($stored -and [int]$stored.Groups[1].Value -ge 2) {
            Write-Ok "[session1] the storehouse holds $($stored.Groups[1].Value) kinds of items"
        } else {
            Add-Failure '[session1] the harvest of the mine did not reach the storehouse'
        }
    }

    # Workshops and orders: far from players a smelter at a furnace and a smith at a crafting table make an iron sword
    # from raw iron through a chain of orders (the sword asks for ingots, the ingots for smelting).
    if ($script:CitizenIds.Count -gt 0) {
        Send-ServerCommand $server "kingdomworld admin citizens spawn 2 $KingdomName"
        if (-not (Wait-ForLog $server "Spawned 2 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] 2 more citizens could not be added'
        }
        $all = Get-CitizenList $server 4 'session1'
        if ($all) {
            $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
            $script:CitizenIds = @($all)
            $script:SmelterId = $new[0]
            $script:SmithId = $new[1]
            Send-ServerCommand $server 'setblock 26 70 4 minecraft:furnace'
            Send-ServerCommand $server 'setblock 26 70 12 minecraft:crafting_table'
            Send-ServerCommand $server 'setblock 24 70 4 kingdomworld:work_post'
            Send-ServerCommand $server 'setblock 24 70 12 kingdomworld:work_post'
            Start-Sleep -Seconds 2
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 4 profession smelter'
            if (-not (Wait-ForLog $server 'Work post at 24 70 4: Smelter, radius \d+' $CommandTimeout 'No work post at 24 70 4|Unknown or incomplete command')) {
                Add-Failure '[session1] the smelter post was not registered or could not be set'
            }
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 12 profession smith'
            if (-not (Wait-ForLog $server 'Work post at 24 70 12: Smith, radius \d+' $CommandTimeout 'No work post at 24 70 12|Unknown or incomplete command')) {
                Add-Failure '[session1] the smithy post was not registered or could not be set'
            }
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:SmelterId) smelter"
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:SmithId) smith"
            if ((Wait-ForLog $server '\S+ is now: Smelter' $CommandTimeout $ConsoleFailPattern) -and (Wait-ForLog $server '\S+ is now: Smith' $CommandTimeout $ConsoleFailPattern)) {
                Write-Ok "[session1] citizens $($script:SmelterId) and $($script:SmithId) became a smelter and a smith"
            } else {
                Add-Failure '[session1] the smelter or the smith could not be appointed'
            }
            foreach ($stock in @('minecraft:raw_iron 2', 'minecraft:coal 2', 'minecraft:stick 1')) {
                Send-ServerCommand $server "kingdomworld admin storage put $stock $KingdomName"
            }
            if (-not (Wait-ForLog $server "Stored 1 of minecraft:stick in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the materials of the sword could not be stored'
            }
            Send-ServerCommand $server "kingdomworld admin orders add minecraft:iron_sword 1 make $KingdomName"
            if (Wait-ForLog $server 'Order #(\d+) given: minecraft:iron_sword, 1 pcs\., make' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the order of an iron sword was given'
            } else {
                Add-Failure '[session1] the order of an iron sword was refused'
            }
            Send-ServerCommand $server "kingdomworld admin orders add minecraft:bread 5 keep $KingdomName"
            $keep = Wait-ForLog $server 'Order #(\d+) given: minecraft:bread, 5 pcs\., keep' $CommandTimeout $ConsoleFailPattern
            if ($keep) {
                $script:KeepOrderId = $keep.Groups[1].Value
                Write-Ok "[session1] a keep order #$($script:KeepOrderId) for bread was given"
            } else {
                Add-Failure '[session1] the keep order for bread was refused'
            }
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 6"
            if (-not (Wait-ForLog $server "Ran 6 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the simulation steps of the workshops did not run'
            }
            Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
            if (Wait-ForLog $server ' - minecraft:iron_sword: 1' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the smith made the iron sword from raw iron through the smelter far from players'
                $script:SwordMade = $true
            } else {
                Add-Failure '[session1] the iron sword was not made (chain smelter -> smith)'
            }
            Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
            if (Wait-ForLog $server "Orders of ${KingdomName}: 1\b" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the finished order left, the keep order stays'
            } else {
                Add-Failure '[session1] expected only the keep order to be left'
            }

            # With the mods of the pack: an item of a mod with a crafting table recipe is made by the smith on order.
            if ($Pack) {
                Send-ServerCommand $server 'kingdomworld admin recipes list smith mods'
                # A weapon or a tool, not armor or other gear that wears out.
                $modItem = Wait-ForLog $server ' - ([a-z0-9_.-]+:[a-z0-9_./-]*(?:sword|axe|shovel|hoe|bow|spear|dagger|hammer|mace|halberd|katana|scythe)[a-z0-9_./-]*): crafting, recipe ' $CommandTimeout 'Smith: 0 recipes'
                if (-not $modItem) {
                    Add-Failure '[session1] no tool or weapon of a mod with a crafting table recipe was found'
                } else {
                    $item = $modItem.Groups[1].Value
                    $script:ModItem = $item
                    Write-Ok "[session1] a smith item of a mod: $item"
                    Send-ServerCommand $server "kingdomworld admin recipes stock $item $KingdomName"
                    if (-not (Wait-ForLog $server "For $([regex]::Escape($item)) \(recipe " $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure "[session1] the materials of $item could not be stored"
                    }
                    Send-ServerCommand $server "kingdomworld admin orders add $item 1 make $KingdomName"
                    if (-not (Wait-ForLog $server "Order #\d+ given: $([regex]::Escape($item)), 1 pcs\." $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure "[session1] the order of $item was refused"
                    }
                    Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 4"
                    if (-not (Wait-ForLog $server "Ran 4 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure '[session1] the simulation steps for the item of a mod did not run'
                    }
                    Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
                    Start-Sleep -Seconds 2
                    Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
                    if (Wait-ForLog $server " - $([regex]::Escape($item)): 1" $CommandTimeout $ConsoleFailPattern) {
                        Write-Ok "[session1] the smith made $item on order"
                    } else {
                        Add-Failure "[session1] the smith did not make $item on order"
                    }
                }
            }
        }
    }

    # Money: the treasury gets 1000 from an operator, a minter far from players strikes silver coins from five iron
    # ingots of the storehouses, a payday pays the wages, and the worth of every item (of the mods too) is worked out.
    if ($script:CitizenIds.Count -gt 0) {
        $rawPattern = 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)'
        Send-ServerCommand $server "kingdomworld admin economy treasury $KingdomName add 1000"
        if (Wait-ForLog $server "Added 1000 to the treasury of $KingdomName, funds 1000" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the treasury got 1000 from the operator'
        } else {
            Add-Failure '[session1] the operator could not add money to the treasury'
        }
        $count = $script:CitizenIds.Count + 1
        Send-ServerCommand $server "kingdomworld admin citizens spawn 1 $KingdomName"
        if (-not (Wait-ForLog $server "Spawned 1 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the minter could not be added'
        }
        $all = Get-CitizenList $server $count 'session1'
        if ($all) {
            $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
            $script:CitizenIds = @($all)
            $script:MinterId = $new[0]
            Send-ServerCommand $server 'setblock 26 70 20 kingdomworld:mint'
            Send-ServerCommand $server 'setblock 24 70 20 kingdomworld:work_post'
            Start-Sleep -Seconds 2
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 20 profession minter'
            if (-not (Wait-ForLog $server 'Work post at 24 70 20: Minter, radius \d+' $CommandTimeout 'No work post at 24 70 20|Unknown or incomplete command')) {
                Add-Failure '[session1] the mint post was not registered or could not be set'
            }
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:MinterId) minter"
            if (Wait-ForLog $server '\S+ is now: Minter' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session1] citizen $($script:MinterId) became a minter"
            } else {
                Add-Failure '[session1] the minter could not be appointed'
            }
            Send-ServerCommand $server "kingdomworld admin storage put minecraft:iron_ingot 5 $KingdomName"
            if (-not (Wait-ForLog $server "Stored 5 of minecraft:iron_ingot in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the iron for the mint could not be stored'
            }
            $runs = Count-Log "Ran 1 simulation step\(s\) of $KingdomName"
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 1"
            [void](Wait-ForNewLog $server "Ran 1 simulation step\(s\) of $KingdomName" $CommandTimeout $runs)
            # The zone of the post is looked at over a few ticks before the minter knows its mint.
            Start-Sleep -Seconds 3
            # With the mods of the pack the smelter makes copper ingots of more ores; the minter needs more time for them all.
            $mintSteps = if ($Pack) { 12 } else { 6 }
            $runs = Count-Log "Ran $mintSteps simulation step\(s\) of $KingdomName"
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName $mintSteps"
            if (-not (Wait-ForNewLog $server "Ran $mintSteps simulation step\(s\) of $KingdomName" $CommandTimeout $runs)) {
                Add-Failure '[session1] the simulation steps of the mint did not run'
            }
            $seen = Count-Log $rawPattern
            Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
            $raw = Wait-ForNewLog $server $rawPattern $CommandTimeout $seen
            # The smelter may have made copper ingots of the miner's ore: the minter strikes them too.
            $funds = 0
            if ($raw) {
                $vault = [int64]$raw.Groups[2].Value + 10 * [int64]$raw.Groups[3].Value + 100 * [int64]$raw.Groups[4].Value
                $funds = [int64]$raw.Groups[5].Value
            }
            # With the mods of the pack the smelter may have made more iron ingots: the minter strikes them too, two silver coins each.
            if ($raw -and [int64]$raw.Groups[1].Value -eq 1000 -and [int64]$raw.Groups[3].Value -ge 10 -and [int64]$raw.Groups[3].Value % 2 -eq 0 -and $funds -eq 1000 + $vault) {
                Write-Ok "[session1] the minter struck at least 10 silver coins from the iron ingots into the vault: $($raw.Value)"
            } else {
                Add-Failure "[session1] expected at least 10 silver coins in the vault and 1000 on the books, got '$(if ($raw) { $raw.Value })'"
            }
            Send-ServerCommand $server "kingdomworld admin economy payday $KingdomName"
            $pay = Wait-ForLog $server "Payday of $KingdomName on day (\d+): tax payers (\d+), taxes (\d+), wages (\d+) of (\d+), citizens (\d+)" $CommandTimeout $ConsoleFailPattern
            if ($pay -and $pay.Groups[4].Value -eq $pay.Groups[5].Value -and [int]$pay.Groups[6].Value -eq $count) {
                Write-Ok "[session1] the payday paid all wages: $($pay.Groups[4].Value) to $count citizens"
            } else {
                Add-Failure "[session1] the payday did not pay the wages: '$(if ($pay) { $pay.Value })'"
            }
            # The mint goes now: later simulation steps would strike what the smelter makes and move the money between the
            # checks that compare it. The post keeps its minter.
            Send-ServerCommand $server 'setblock 26 70 20 minecraft:air'
            $seen = Count-Log $rawPattern
            Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
            $raw = Wait-ForNewLog $server $rawPattern $CommandTimeout $seen
            if ($raw -and $pay -and [int64]$raw.Groups[5].Value -eq ($funds - [int64]$pay.Groups[4].Value)) {
                $script:EconomyRaw = $raw.Value
                Write-Ok "[session1] the wages left the treasury: $($raw.Value)"
            } else {
                Add-Failure "[session1] the treasury after the payday is wrong: '$(if ($raw) { $raw.Value })'"
            }
        }
        Send-ServerCommand $server 'kingdomworld admin economy values'
        $values = Wait-ForLog $server 'Item values: (\d+) items \((\d+) from recipes, (\d+) from the config, (\d+) by default\), (\d+) recipes, (\d+) unreadable' $CommandTimeout $ConsoleFailPattern
        if ($values -and [int]$values.Groups[1].Value -gt 1000 -and [int]$values.Groups[2].Value -gt 300) {
            Write-Ok "[session1] item values: $($values.Value)"
            if ([int]$values.Groups[6].Value -gt 0) {
                Add-Warning "[session1] $($values.Groups[6].Value) recipe(s) of mods could not be read for item values"
            }
        } else {
            Add-Failure "[session1] the worth of items was not worked out: '$(if ($values) { $values.Value })'"
        }
        Send-ServerCommand $server 'kingdomworld admin economy value minecraft:iron_sword'
        if (Wait-ForLog $server 'is worth .+ \(from recipes; minecraft:iron_sword = (\d+)\)' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] an iron sword is worth what its recipe costs'
        } else {
            Add-Failure '[session1] the worth of an iron sword was not worked out from its recipe'
        }
    }

    # The army: citizens are recruited as warriors and an archer and take the best weapons of the storehouses (with -Pack the
    # weapons of Simply Swords and of L'Ender's Cataclysm with Cataclysmic Combat), a squad of them holds a point and a band
    # of mercenaries is hired for its advance from the treasury. The money of the advance leaves the world; everything must
    # survive the restart, and nothing of it may make money or items.
    if ($script:CitizenIds.Count -gt 0) {
        # Daylight all the time: squads that hold a point far from players meet monsters at night.
        Send-ServerCommand $server 'gamerule doDaylightCycle false'
        Send-ServerCommand $server 'time set 1000'
        Send-ServerCommand $server 'kingdomworld admin army status'
        if (Wait-ForLog $server 'Army data is writable, format 1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the army data is writable'
        } else {
            Add-Failure '[session1] the army data is not writable'
        }
        # Not the smith's iron sword: it stays in the storehouse.
        $weapons = @('minecraft:stone_sword', 'minecraft:bow')
        $expected = @()
        if (-not $Pack) {
            $weapons += 'minecraft:diamond_sword'
            $expected += 'minecraft:diamond_sword'
        }
        if ($Pack) {
            foreach ($namespace in @('simplyswords', 'cataclysm')) {
                $seen = Count-Log "Weapons of $namespace soldiers would take: (\d+)"
                Send-ServerCommand $server "kingdomworld admin army weapons $namespace"
                $header = Wait-ForNewLog $server "Weapons of $namespace soldiers would take: (\d+)" $CommandTimeout $seen
                Start-Sleep -Seconds 1
                $best = [regex]::Match((Get-LatestLog), "Weapon ($namespace`:[a-z0-9_./-]+): melee ([\d.]+)")
                if ($header -and [int]$header.Groups[1].Value -gt 0 -and $best.Success -and [double]$best.Groups[2].Value -le 10) {
                    Add-Warning "[session1] the best weapon of $namespace ($($best.Groups[1].Value), $($best.Groups[2].Value)) is no better than an iron sword"
                } elseif ($header -and [int]$header.Groups[1].Value -gt 0 -and $best.Success) {
                    Write-Ok "[session1] $($header.Groups[1].Value) weapons of $namespace judged, the best for melee $($best.Groups[1].Value) ($($best.Groups[2].Value))"
                    $weapons += $best.Groups[1].Value
                    $expected += $best.Groups[1].Value
                } else {
                    Add-Failure "[session1] no melee weapon of $namespace was found for soldiers"
                }
            }
        }
        $warriors = [Math]::Max(1, $expected.Count)
        $script:ArmySoldiers = $warriors + 1
        $count = $script:CitizenIds.Count + $script:ArmySoldiers
        Send-ServerCommand $server "kingdomworld admin citizens spawn $($script:ArmySoldiers) $KingdomName"
        [void](Wait-ForLog $server "Spawned $($script:ArmySoldiers) citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)
        $all = Get-CitizenList $server $count 'session1'
        if ($all) { $script:CitizenIds = @($all) }
        foreach ($weapon in $weapons) {
            Send-ServerCommand $server "kingdomworld admin storage put $weapon 1 $KingdomName"
            [void](Wait-ForLog $server "Stored 1 of $([regex]::Escape($weapon)) in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
        }
        Send-ServerCommand $server "kingdomworld admin army recruit $KingdomName $warriors warrior"
        $recruited = Wait-ForLog $server "Recruited (\d+) citizen\(s\) as warrior in $KingdomName" $CommandTimeout $ConsoleFailPattern
        Send-ServerCommand $server "kingdomworld admin army recruit $KingdomName 1 archer"
        $archers = Wait-ForLog $server "Recruited (\d+) citizen\(s\) as archer in $KingdomName" $CommandTimeout $ConsoleFailPattern
        if ($recruited -and [int]$recruited.Groups[1].Value -eq $warriors -and $archers -and $archers.Groups[1].Value -eq '1') {
            Write-Ok "[session1] $warriors warrior(s) and an archer recruited"
        } else {
            Add-Failure '[session1] the soldiers were not recruited'
        }
        Start-Sleep -Seconds 1
        $log = Get-LatestLog
        foreach ($weapon in $expected + @('minecraft:bow')) {
            if ($log -match "Soldier [0-9a-f]{8} took $([regex]::Escape($weapon)) for mainhand") {
                Write-Ok "[session1] a soldier took $weapon"
            } else {
                Add-Failure "[session1] no soldier took $weapon"
            }
        }
        foreach ($line in ([regex]::Matches($log, 'Soldier [0-9a-f]{8} took [^\r\n]+') | Select-Object -First 12)) { Write-Host "      $($line.Value)" }
        $script:ArmyWeapons = @($expected + @('minecraft:bow'))
        Send-ServerCommand $server "kingdomworld admin army squad form $KingdomName $($script:ArmySoldiers)"
        $squad = Wait-ForLog $server "Squad #(\d+) of $KingdomName formed: members $($script:ArmySoldiers)" $CommandTimeout $ConsoleFailPattern
        if ($squad) {
            $script:ArmySquad = $squad.Groups[1].Value
            Send-ServerCommand $server "kingdomworld admin army order $($script:ArmySquad) hold 20 70 20"
            if (Wait-ForLog $server "Squad #$($script:ArmySquad) got the order hold at 20 70 20" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session1] squad #$($script:ArmySquad) holds the point 20 70 20"
            } else {
                Add-Failure '[session1] the squad did not get its order'
            }
        } else {
            Add-Failure '[session1] the squad of soldiers was not formed'
        }
        $offersPattern = "Band #(\d+): (\d+) fighter\(s\), rank \w+, power (\d+), (\d+) a day, advance (\d+)"
        $seen = Count-Log $offersPattern
        Send-ServerCommand $server "kingdomworld admin army mercenary offers $KingdomName new"
        $offer = Wait-ForNewLog $server $offersPattern $CommandTimeout $seen
        if ($offer) {
            # The offers are random: take the cheapest of the new ones, so that the treasury can pay its advance.
            Start-Sleep -Seconds 1
            $offer = @([regex]::Matches((Get-LatestLog), $offersPattern) | Select-Object -Skip $seen | Sort-Object { [int64]$_.Groups[5].Value })[0]
            $before = Get-KingdomMoney $server $KingdomName
            $advance = [int64]$offer.Groups[5].Value
            Send-ServerCommand $server "kingdomworld admin army mercenary hire $KingdomName $($offer.Groups[1].Value)"
            $hired = Wait-ForLog $server "Band #$($offer.Groups[1].Value) hired by $KingdomName as squad #(\d+), paid until day (-?\d+)" $CommandTimeout $ConsoleFailPattern
            $after = Get-KingdomMoney $server $KingdomName
            if ($hired -and $after -eq $before - $advance) {
                $script:ArmyBand = $offer.Groups[1].Value
                $script:ArmyBandSquad = $hired.Groups[1].Value
                $script:ArmyPaidUntil = $hired.Groups[2].Value
                Write-Ok "[session1] band #$($script:ArmyBand) of $($offer.Groups[2].Value) mercenaries hired as squad #$($script:ArmyBandSquad), the advance of $advance left the world: $before -> $after"
            } else {
                Add-Failure "[session1] hiring mercenaries went wrong: '$(if ($hired) { $hired.Value })', money $before -> $after, advance $advance"
            }
        } else {
            Add-Failure '[session1] no mercenaries were offered'
        }
    }

    if ($script:CreatedId) {
        # Magic: crystals become energy one for one, research and spells of the kingdom, the wells of other mods with -Pack.
        # It runs before the money of trade and the guild is measured: the minter keeps striking coins meanwhile.
        $magic = Get-KingdomMagic $server $KingdomName
        if ($magic -and $magic.Groups[1].Value -eq '0') {
            Write-Ok "[session1] the kingdom starts without energy, capacity $($magic.Groups[2].Value)"
        } else {
            Add-Failure "[session1] energy appeared from nothing: $($magic.Value)"
        }
        Send-ServerCommand $server "kingdomworld admin magic crystals $KingdomName e 4"
        if (Wait-ForLog $server "Put 4 crystal\(s\) of rank E into the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern) {
            $crystals = Get-KingdomCrystals $server $KingdomName
            $crystalEnergy = if ($crystals) { [int64]$crystals.Groups[2].Value } else { -1 }
            Send-ServerCommand $server "kingdomworld admin magic dissolve $KingdomName 4"
            $dissolved = Wait-ForLog $server "$KingdomName dissolved crystals for (\d+) energy, now (\d+)" $CommandTimeout $ConsoleFailPattern
            $magic = Get-KingdomMagic $server $KingdomName
            if ($dissolved -and $magic -and [int64]$dissolved.Groups[1].Value -eq $crystalEnergy -and [int64]$magic.Groups[1].Value -eq $crystalEnergy `
                    -and $magic.Groups[3].Value -eq '0') {
                Write-Ok "[session1] 4 crystals with $crystalEnergy energy became exactly $crystalEnergy energy, no crystal is left"
            } else {
                Add-Failure "[session1] dissolving changed the sum of crystals and energy: crystals $crystalEnergy, $($dissolved.Value), $($magic.Value)"
            }
        } else {
            Add-Failure '[session1] crystals could not be put into the storehouses'
        }
        Send-ServerCommand $server "kingdomworld admin magic energy $KingdomName add 1000"
        [void](Wait-ForLog $server "Energy of ${KingdomName}: (\d+)" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin magic research $KingdomName ward done"
        Send-ServerCommand $server "kingdomworld admin magic research $KingdomName gates done"
        Send-ServerCommand $server "kingdomworld admin magic research $KingdomName arcana_1 start"
        [void](Wait-ForLog $server "Research arcana_1 of $KingdomName started" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin magic spell $KingdomName gates on"
        if (Wait-ForLog $server "Spell gates of $KingdomName switched on" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] research done and started, the gates switched on'
        } else {
            Add-Failure '[session1] the spell of the gates did not switch on'
        }
        $script:Magic = Get-KingdomMagic $server $KingdomName
        if ($script:Magic -and $script:Magic.Groups[5].Value -eq 'ward,gates' -and $script:Magic.Groups[6].Value -eq 'arcana_1' `
                -and $script:Magic.Groups[7].Value -eq 'gates') {
            Write-Ok "[session1] magic before the restart: energy $($script:Magic.Groups[1].Value), research $($script:Magic.Groups[5].Value), spells $($script:Magic.Groups[7].Value)"
        } else {
            Add-Failure "[session1] magic is not what was set: $($script:Magic.Value)"
        }
        if ($Pack) {
            foreach ($mod in @("Iron's Spells 'n Spellbooks", 'Ars Nouveau')) {
                if ((Get-LatestLog) -match "$([regex]::Escape($mod)) \S+ detected") {
                    Write-Ok "[session1] the wells of magic foci serve $mod"
                } else {
                    Add-Warning "[session1] $mod is not in the pack or its integration did not start"
                }
            }
            Send-ServerCommand $server 'setblock 4 70 12 ars_nouveau:source_jar'
            Start-Sleep -Seconds 2
            $seen = Count-Log 'KingdomWorld magic well check at '
            Send-ServerCommand $server "kingdomworld admin magic wells $KingdomName 4 70 12"
            $wells = Wait-ForLog $server "Wells of ${KingdomName}: (\d+) report\(s\), (\d+) energy spent" $CommandTimeout $ConsoleFailPattern
            Start-Sleep -Seconds 1
            $lines = @([regex]::Matches((Get-LatestLog), 'KingdomWorld magic well check at [^\r\n]+') | ForEach-Object { $_.Value })
            foreach ($line in $lines) { Write-Host "      $line" }
            $log = Get-LatestLog
            if ($log -match "KingdomWorld magic well check at [^\r\n]*Iron's Spells mana \+\d+") {
                Write-Ok "[session1] the well refilled the mana of Iron's Spells"
            } else {
                Add-Failure "[session1] the well did not refill the mana of Iron's Spells"
            }
            if ($log -match 'KingdomWorld magic well check at [^\r\n]*Ars Nouveau source \+\d+') {
                Write-Ok '[session1] the focus filled a source jar of Ars Nouveau'
            } else {
                Add-Failure '[session1] the focus did not fill the source jar of Ars Nouveau'
            }
            if ($log -match 'KingdomWorld magic well check at [^\r\n]*Ars Nouveau mana \+\d+') {
                Write-Ok '[session1] the well refilled the mana of Ars Nouveau'
            } else {
                Add-Warning '[session1] the well gave no mana of Ars Nouveau to the stand-in player (Ars knows no maximum for it)'
            }
            Send-ServerCommand $server 'setblock 4 70 12 minecraft:air'
            $script:Magic = Get-KingdomMagic $server $KingdomName
        }
    }

    # Trade: a second kingdom far away buys wheat in a deal; its caravan leaves with the payment in escrow and is still on
    # the way when the server stops. A test buyer buys wheat at the market from the account. No money appears anywhere.
    Send-ServerCommand $server "kingdomworld admin found $PartnerName 640 70 8"
    if (Wait-ForLog $server "Founded kingdom $PartnerName \(id ($IdPattern)\) with (\d+) chunk\(s\)" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok "[session1] the partner kingdom $PartnerName was founded"
    } else {
        Add-Failure "[session1] the partner kingdom $PartnerName was not founded"
    }
    Send-ServerCommand $server 'forceload add 624 -8 655 23'
    Start-Sleep -Seconds 3
    Send-ServerCommand $server 'setblock 641 70 9 kingdomworld:storehouse'
    Start-Sleep -Seconds 2
    Send-ServerCommand $server "kingdomworld admin economy treasury $PartnerName add 500"
    if (-not (Wait-ForLog $server "Added 500 to the treasury of $PartnerName, funds 500" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure "[session1] the treasury of $PartnerName got no money"
    }
    # Roads: a city of SmokeTest, a path over the wild land between SmokeTest and the partner built by the operator command,
    # and a camp of robbers far from the way. The caravan of the deal below takes the road. All of it must survive the
    # restart. Consent to cross foreign land is covered by the game tests.
    Send-ServerCommand $server 'kingdomworld admin roads status'
    if (Wait-ForLog $server 'Roads: 0 cities, 0 roads, camps: 0, caravans on roads: 0, data format 1' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the road data is writable and empty'
    } else {
        Add-Failure '[session1] the road data is not writable or not empty'
    }
    Send-ServerCommand $server "kingdomworld admin roads city add $KingdomName 40 100 8 Smoke Hollow"
    if (Wait-ForLog $server "A city of $KingdomName founded at 40 100 8" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the city Smoke Hollow was founded'
    } else {
        Add-Failure '[session1] the city Smoke Hollow was not founded'
    }
    # The land around the city is levelled, so that the road starts and the ring of the wall stands on the ground in every
    # world (the kingdom is near the spawn, often by the sea); the town hall above stays.
    Send-ServerCommand $server 'forceload add 34 2 46 14'
    Send-ServerCommand $server 'fill 34 71 2 46 90 14 minecraft:air'
    Send-ServerCommand $server 'fill 34 69 2 46 69 14 minecraft:dirt'
    Send-ServerCommand $server 'fill 34 70 2 46 70 14 minecraft:grass_block'
    # The wild land between the city and the partner is levelled along z = 8, so that the way of the road is the same in
    # every world.
    Send-ServerCommand $server 'forceload add 46 0 591 16'
    Start-Sleep -Seconds 5
    # Up to the top of the world, so that no hill or tree (mountains of mods are high) stands over the strip; at most
    # 32768 blocks a fill.
    for ($x = 46; $x -le 591; $x += 40) {
        $end = [Math]::Min($x + 39, 591)
        Send-ServerCommand $server "fill $x 71 7 $end 319 9 minecraft:air"
        Send-ServerCommand $server "fill $x 69 7 $end 69 9 minecraft:dirt"
        Send-ServerCommand $server "fill $x 70 7 $end 70 9 minecraft:grass_block"
    }
    Start-Sleep -Seconds 3
    # Planks and earth for bridges and embankments where the way leaves the strip, so that the road is paid in full and
    # builders never reach for the materials of the house.
    Send-ServerCommand $server "kingdomworld admin storage put minecraft:oak_planks 640 $KingdomName"
    [void](Wait-ForLog $server "Stored \d+ of minecraft:oak_planks in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin storage put minecraft:dirt 320 $KingdomName"
    [void](Wait-ForLog $server "Stored \d+ of minecraft:dirt in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin roads road plan $KingdomName 64 70 8 580 70 8 path"
    $planned = Wait-ForLog $server "Road (\d+) of $KingdomName planned: (\d+) blocks, path, (\d+) request\(s\) to cross land" 300 "No way for a road of $KingdomName|$ConsoleFailPattern"
    if ($planned) {
        $script:RoadId = $planned.Groups[1].Value
        Write-Ok "[session1] road #$($script:RoadId) planned over the land between the kingdoms: $($planned.Groups[2].Value) blocks"
        Send-ServerCommand $server "kingdomworld admin roads road pay $($script:RoadId)"
        $paid = Wait-ForLog $server "Road #$($script:RoadId): (\d+) blocks paid from the storehouses, (\d+) of (\d+) in all" $CommandTimeout $ConsoleFailPattern
        if ($paid -and [int]$paid.Groups[2].Value -gt 0 -and $paid.Groups[2].Value -eq $paid.Groups[3].Value) {
            $script:RoadPaid = $paid.Groups[2].Value
            $script:RoadUnits = $paid.Groups[3].Value
            Write-Ok "[session1] road #$($script:RoadId) built: $($script:RoadPaid) of $($script:RoadUnits) blocks"
        } else {
            Add-Failure "[session1] road #$($script:RoadId) could not be built: '$(if ($paid) { $paid.Value })'"
        }
        Send-ServerCommand $server "kingdomworld admin roads road place $($script:RoadId)"
        if (Wait-ForLog $server "Road #$($script:RoadId): (\d+) blocks placed" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] the path of road #$($script:RoadId) is in the world"
        } else {
            Add-Failure "[session1] the blocks of road #$($script:RoadId) were not placed"
        }
    } else {
        # The world is new every time: where its land (water running into the strip, say) leaves no way, the road checks
        # are skipped; the game tests cover the search.
        Add-Warning "[session1] no road was planned between $KingdomName and $PartnerName in this world; road checks skipped"
    }
    # A palisade around Smoke Hollow, a village: laid out on the lay of the land with a gate where the road leaves the city,
    # paid from logs put into the storehouses, placed and entered into the register of buildings, and a gate cut by hand on
    # its north side. It must survive the restart.
    Send-ServerCommand $server "kingdomworld admin roads city level $KingdomName 1"
    if (Wait-ForLog $server 'Smoke Hollow: village, citizens \d+, buildings \d+, 0 gates' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] Smoke Hollow is a village without gates'
    } else {
        Add-Failure '[session1] the level of Smoke Hollow is wrong'
    }
    Send-ServerCommand $server "kingdomworld admin storage put minecraft:oak_log 128 $KingdomName"
    $logs = Wait-ForLog $server "Stored (\d+) of minecraft:oak_log in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern
    Send-ServerCommand $server "kingdomworld admin roads wall build $KingdomName 1 palisade"
    $wall = Wait-ForLog $server 'Wall #(\d+) laid out: (\d+) columns, gates (\d+)|There is no ground around the city for a wall' $CommandTimeout $ConsoleFailPattern
    if ($wall -and -not $wall.Groups[1].Success) {
        # The world is new every time: a city in the sea leaves no ground for a ring; the game tests cover walls.
        Add-Warning '[session1] no ground around Smoke Hollow for a wall in this world; wall checks skipped'
    } elseif ($wall) {
        $script:WallId = $wall.Groups[1].Value
        $columns = [int]$wall.Groups[2].Value
        $gates = [int]$wall.Groups[3].Value
        Write-Ok "[session1] wall #$($script:WallId) laid out around Smoke Hollow: $columns columns, $gates gate(s)"
        if ($script:RoadId) {
            if ($gates -ge 1) {
                Write-Ok '[session1] the road that leaves Smoke Hollow got a gate in the wall by itself'
            } else {
                Add-Failure '[session1] the road that leaves Smoke Hollow got no gate in the wall'
            }
        }
        if (-not $logs -or [int]$logs.Groups[1].Value -lt $columns * 4) {
            Add-Warning "[session1] the storehouses took too few logs for the wall: '$(if ($logs) { $logs.Value })'; wall checks skipped"
            $script:WallId = $null
        }
    } else {
        Add-Failure '[session1] no wall was laid out around Smoke Hollow'
    }
    if ($script:WallId) {
        Send-ServerCommand $server "kingdomworld admin roads wall pay $($script:WallId)"
        $wallPaid = Wait-ForLog $server "Wall #$($script:WallId): (\d+) more columns paid, (\d+) in all" $CommandTimeout $ConsoleFailPattern
        if ($wallPaid -and $wallPaid.Groups[1].Value -eq $wallPaid.Groups[2].Value) {
            Write-Ok "[session1] wall #$($script:WallId) paid from the storehouses: $($wallPaid.Groups[1].Value) columns"
        } else {
            Add-Failure "[session1] wall #$($script:WallId) was not paid in full: '$(if ($wallPaid) { $wallPaid.Value })'"
        }
        Send-ServerCommand $server "kingdomworld admin roads wall place $($script:WallId)"
        if (Wait-ForLog $server "Wall $($script:WallId) is built and entered into the register as building \d+" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] wall #$($script:WallId) stands and is a fortification in the register"
        } else {
            Add-Failure "[session1] wall #$($script:WallId) was not placed or not registered"
        }
        Send-ServerCommand $server "kingdomworld admin roads wall gate $($script:WallId) 40 70 2"
        if (Wait-ForLog $server "Wall #$($script:WallId) now has gates: $($gates + 1)" $CommandTimeout "Stand by the wall|$ConsoleFailPattern") {
            Write-Ok "[session1] a gate was cut in the north side of wall #$($script:WallId)"
        } else {
            # Water may leave a gap in the ring there.
            Add-Warning "[session1] no gate could be cut in wall #$($script:WallId) at 40 2 in this world"
        }
        Send-ServerCommand $server 'kingdomworld admin roads wall list'
        $wallRow = Wait-ForLog $server "#$($script:WallId) of city 1 \($KingdomName\): palisade, (\d+) of (\d+) columns stand, gates (\d+), register (\d+)" $CommandTimeout $ConsoleFailPattern
        if ($wallRow) {
            $script:WallRow = $wallRow.Value
        } else {
            Add-Failure "[session1] wall #$($script:WallId) is not in the list of walls"
        }
    }
    Send-ServerCommand $server 'kingdomworld admin roads camp add -3000 70 -3000 5'
    if (Wait-ForLog $server 'Camp #1 at -3000 -3000: 5 robbers and a leader' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] a camp of robbers with 5 robbers and a leader appeared far from the way'
    } else {
        Add-Failure '[session1] the camp of robbers was not made'
    }
    Send-ServerCommand $server 'kingdomworld admin roads gear'
    $gear = Wait-ForLog $server 'Robbers armed: (\d+) of 64, items of (\d+) mods' $CommandTimeout $ConsoleFailPattern
    if ($gear -and [int]$gear.Groups[1].Value -gt 0) {
        Write-Ok "[session1] robbers took weapons and armor of $($gear.Groups[2].Value) mod(s), $($gear.Groups[1].Value) of 64 armed"
        if ($Pack -and [int]$gear.Groups[2].Value -lt 2) { Add-Warning '[session1] robbers took no weapon or armor of a mod of the pack' }
    } else {
        Add-Failure '[session1] robbers could not be armed'
    }

    Send-ServerCommand $server "kingdomworld admin storage put minecraft:wheat 64 $KingdomName"
    if (-not (Wait-ForLog $server "Stored 64 of minecraft:wheat in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the wheat for the deal could not be stored'
    }
    $ratePattern = '(\S+): rate (\d+), money (\d+), coins in hands (\d+), treasury (\d+), reserves (\d+), exported today (\d+)'
    Send-ServerCommand $server "kingdomworld admin trade propose $KingdomName $PartnerName minecraft:wheat 32 100"
    if (-not (Wait-ForLog $server "The proposal went to $PartnerName" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the deal was not proposed'
    }
    Send-ServerCommand $server 'kingdomworld admin trade deals'
    $deal = Wait-ForLog $server "#(\d+) $KingdomName -> ${PartnerName}: minecraft:wheat x32 for 100, proposed, deliveries 0" $CommandTimeout $ConsoleFailPattern
    if ($deal) {
        Send-ServerCommand $server "kingdomworld admin trade accept $PartnerName $($deal.Groups[1].Value)"
        if (Wait-ForLog $server 'The deal is accepted and the caravan left' $CommandTimeout 'Unknown or incomplete command|Incorrect argument|read-only|too few|too little') {
            Write-Ok "[session1] $PartnerName accepted deal #$($deal.Groups[1].Value) and the caravan left"
        } else {
            Add-Failure '[session1] the deal was not accepted'
        }
    } else {
        Add-Failure '[session1] the proposed deal is not listed'
    }
    Send-ServerCommand $server 'kingdomworld admin trade caravans'
    $caravan = Wait-ForLog $server "#(\d+) $KingdomName -> ${PartnerName}: minecraft:wheat x32, payment 100, traveling, (\d+) ticks left" $CommandTimeout $ConsoleFailPattern
    if ($caravan -and [int64]$caravan.Groups[2].Value -gt 1200) {
        $script:CaravanId = $caravan.Groups[1].Value
        Write-Ok "[session1] caravan #$($script:CaravanId) is on the way, $($caravan.Groups[2].Value) ticks left"
    } else {
        Add-Failure "[session1] no caravan on a long way: '$(if ($caravan) { $caravan.Value })'"
    }
    if ($script:CaravanId -and $script:RoadId) {
        Send-ServerCommand $server 'kingdomworld admin roads caravans'
        $way = Wait-ForLog $server "Caravan #$($script:CaravanId) \(deal #\d+\) traveling at -?\d+ -?\d+: (\d+) points, (\d+) roads, (\d+) of (\d+) blocks on roads, 32 items" $CommandTimeout $ConsoleFailPattern
        if ($way -and [int]$way.Groups[2].Value -ge 1 -and [int]$way.Groups[3].Value -gt 0) {
            $script:CaravanWay = "$($way.Groups[1].Value) points, $($way.Groups[2].Value) roads, $($way.Groups[3].Value) of $($way.Groups[4].Value) blocks on roads"
            Write-Ok "[session1] the caravan takes the road: $($script:CaravanWay)"
        } else {
            Add-Failure "[session1] the caravan does not take the road: '$(if ($way) { $way.Value })'"
        }
    }
    Send-ServerCommand $server "kingdomworld admin trade sell $KingdomName minecraft:wheat 5 0"
    if (-not (Wait-ForLog $server 'The kingdom sells Wheat' $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the market does not sell wheat'
    }
    $seen = Count-Log $ratePattern
    Send-ServerCommand $server 'kingdomworld admin trade rates'
    [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
    Start-Sleep -Seconds 1
    $before = [regex]::Matches((Get-LatestLog), "${KingdomName}: rate \d+, money (\d+),")
    $moneyBefore = if ($before.Count -gt 0) { [int64]$before[$before.Count - 1].Groups[1].Value } else { -1 }
    Send-ServerCommand $server "kingdomworld admin trade wallet $KingdomName SmokeBuyer 100"
    if (-not (Wait-ForLog $server "Account of SmokeBuyer in the currency of ${KingdomName}: 100" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the test buyer got no money'
    }
    Send-ServerCommand $server "kingdomworld admin trade purchase $KingdomName SmokeBuyer minecraft:wheat 3"
    if (Wait-ForLog $server 'Bought Wheat: 3 items for' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the test buyer bought 3 wheat at the market from the account'
    } else {
        Add-Failure '[session1] buying at the market failed'
    }
    $seen = Count-Log $ratePattern
    Send-ServerCommand $server 'kingdomworld admin trade rates'
    [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
    Start-Sleep -Seconds 1
    $log = Get-LatestLog
    $mine = [regex]::Matches($log, "${KingdomName}: rate \d+, money (\d+),")
    $theirs = [regex]::Matches($log, "${PartnerName}: rate \d+, money (\d+),")
    $moneyAfter = if ($mine.Count -gt 0) { [int64]$mine[$mine.Count - 1].Groups[1].Value } else { -2 }
    $partnerMoney = if ($theirs.Count -gt 0) { [int64]$theirs[$theirs.Count - 1].Groups[1].Value } else { -1 }
    if ($moneyAfter -eq $moneyBefore + 100 -and $partnerMoney -eq 500) {
        $script:TradeMoney = "$moneyAfter/$partnerMoney"
        Write-Ok "[session1] no money appeared: $KingdomName $moneyBefore + 100 from the operator = $moneyAfter, $PartnerName 500 with 100 in escrow"
    } else {
        Add-Failure "[session1] money changed in trade: $KingdomName $moneyBefore -> $moneyAfter (expected +100), $PartnerName $partnerMoney (expected 500)"
    }
    # The guild: adventurers form a squad, the kingdom posts two contracts paid from its treasury into escrow, the squad
    # leaves for one of them. Nothing of it may make money or crystals, and all of it must survive the restart.
    if ($script:CitizenIds.Count -gt 0) {
        Send-ServerCommand $server 'kingdomworld admin guild status'
        if (Wait-ForLog $server 'Guild data is writable, format 1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the guild data is writable'
        } else {
            Add-Failure '[session1] the guild data is not writable'
        }
        $count = $script:CitizenIds.Count + 2
        Send-ServerCommand $server "kingdomworld admin citizens spawn 2 $KingdomName"
        [void](Wait-ForLog $server "Spawned 2 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)
        $all = Get-CitizenList $server $count 'session1'
        if ($all) { $script:CitizenIds = @($all) }
        Send-ServerCommand $server "kingdomworld admin guild hire $KingdomName 2"
        if (-not (Wait-ForLog $server "Adventurers made: 2 in $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] two citizens did not become adventurers'
        }
        # Wooden swords for the adventurers: they take the weakest weapon that will do and leave the smith's iron sword.
        Send-ServerCommand $server "kingdomworld admin storage put minecraft:wooden_sword 2 $KingdomName"
        [void](Wait-ForLog $server "Stored 2 of \S+ in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin guild squad form $KingdomName 2"
        $squad = Wait-ForLog $server "Formed squad #(\d+) of $KingdomName, members: 2" $CommandTimeout $ConsoleFailPattern
        $guildMoney = Get-KingdomMoney $server $KingdomName
        $seen = Count-Log 'Posted contract #(\d+) at '
        Send-ServerCommand $server "kingdomworld admin guild contract post $KingdomName hunt `"minecraft:zombie`" 3 100 2"
        $hunt = Wait-ForNewLog $server 'Posted contract #(\d+) at ' $CommandTimeout $seen
        Send-ServerCommand $server "kingdomworld admin guild contract post $KingdomName bounty `"minecraft:ravager`" 1 50 3"
        $bounty = Wait-ForNewLog $server 'Posted contract #(\d+) at ' $CommandTimeout ($seen + 1)
        if ($squad -and $hunt -and $bounty) {
            $script:GuildSquad = $squad.Groups[1].Value
            $script:GuildHunt = $hunt.Groups[1].Value
            $script:GuildBounty = $bounty.Groups[1].Value
            Send-ServerCommand $server "kingdomworld admin guild squad send $($script:GuildSquad) $($script:GuildHunt)"
            if (Wait-ForLog $server "Squad #$($script:GuildSquad) left for contract #$($script:GuildHunt), back in (\d+) ticks" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session1] squad #$($script:GuildSquad) left for hunt #$($script:GuildHunt), bounty #$($script:GuildBounty) waits on the board"
            } else {
                Add-Failure '[session1] the squad did not leave'
            }
            Send-ServerCommand $server 'kingdomworld admin guild money'
            if (Wait-ForLog $server "Held by the guild in the currency of ${KingdomName}: 150, money of the currency in all: (\d+)" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the rewards of both contracts (150) are in escrow'
            } else {
                Add-Failure '[session1] the guild does not hold 150 in escrow'
            }
            $moneyNow = Get-KingdomMoney $server $KingdomName
            if ($guildMoney -ge 0 -and $moneyNow -eq $guildMoney) {
                $script:GuildMoney = $moneyNow
                Write-Ok "[session1] posting into escrow made no money: $guildMoney before, $moneyNow after"
            } else {
                Add-Failure "[session1] posting into escrow changed the money: $guildMoney -> $moneyNow"
            }
            $crystals = Get-KingdomCrystals $server $KingdomName
            if ($crystals -and $crystals.Groups[1].Value -eq '0') {
                Write-Ok '[session1] no crystals before any monster died'
            } else {
                Add-Failure "[session1] crystals appeared from nothing: $($crystals.Value)"
            }
        } else {
            Add-Failure '[session1] the squad or the contracts of the guild were not made'
        }
        # Ranks of monsters: vanilla always, the bosses of the mods of the pack with -Pack.
        $seen = Count-Log 'Monster ranks: (\d+)'
        Send-ServerCommand $server 'kingdomworld admin guild monsters'
        $ranks = Wait-ForNewLog $server 'Monster ranks: (\d+)' $CommandTimeout $seen
        Start-Sleep -Seconds 2
        $log = Get-LatestLog
        if ($ranks -and $log -match 'minecraft:zombie: rank E' -and $log -match 'minecraft:wither: rank SS') {
            Write-Ok "[session1] $($ranks.Groups[1].Value) kinds of monsters ranked, the zombie E and the wither SS"
        } else {
            Add-Failure '[session1] monsters were not ranked as expected'
        }
        if ($log -match 'Could not rank ') {
            Add-Failure "[session1] some monsters could not be ranked: $([regex]::Matches($log, 'Could not rank [^\r\n]+') | Select-Object -First 3)"
        }
        if ($Pack) {
            $bosses = @([regex]::Matches($log, '(?m)(\w+:[\w/]+): rank (\w+), strength (\d+), boss') | ForEach-Object { $_.Value } | Select-Object -Unique)
            $modded = @($bosses | Where-Object { $_ -notmatch '^minecraft:' })
            if ($modded.Count -gt 0) {
                Write-Ok "[session1] bosses of the mods of the pack: $($modded.Count)"
                foreach ($boss in ($modded | Select-Object -First 12)) { Write-Host "      $boss" }
            } else {
                Add-Warning '[session1] no boss of a mod was found in the pack'
            }
        }
    }
    if ($MapModsDir -and $script:CaravanId) { Test-MapMarkers $server 'session1' }

    # Companions: a player who is offline gets money on the account and buys a slave on the market of the kingdom; the price
    # goes from the account to the treasury and no money appears. Iron goes into the pack, the player forms a party. With
    # -Pack the companion takes a weapon of a mod of the pack over an iron sword. All of it must survive the restart.
    Send-ServerCommand $server 'kingdomworld admin companions status'
    if (Wait-ForLog $server 'Companion data format 1: 0 slave\(s\), 0 free companion\(s\), 0 part\(ies\)' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the companion data is writable and empty'
    } else {
        Add-Failure '[session1] the companion data is not writable or not empty'
    }
    $before = Get-KingdomMoney $server $KingdomName
    Send-ServerCommand $server "kingdomworld admin companions fund $($script:CompanionOwner) $KingdomName 5000"
    [void](Wait-ForLog $server 'Account of .* in the currency: \+' $CommandTimeout $ConsoleFailPattern)
    $funded = Get-KingdomMoney $server $KingdomName
    Send-ServerCommand $server "kingdomworld admin companions buy $($script:CompanionOwner) $KingdomName"
    $bought = Wait-ForLog $server ' bought the slave .* for (\d+), id ' $CommandTimeout $ConsoleFailPattern
    $after = Get-KingdomMoney $server $KingdomName
    if (-not $bought) {
        Add-Failure '[session1] the slave could not be bought'
    } elseif ($before -ge 0 -and $funded -eq $before + 5000 -and $after -eq $funded) {
        $script:CompanionMoney = $after
        Write-Ok "[session1] a slave bought for $($bought.Groups[1].Value): the price went to the treasury, no money appeared ($funded before, $after after)"
    } else {
        Add-Failure "[session1] buying a slave changed the money: $before, funded $funded, after $after"
    }
    Send-ServerCommand $server "kingdomworld admin companions pack $($script:CompanionOwner) minecraft:iron_ingot 7"
    if (-not (Wait-ForLog $server 'Put 7 of minecraft:iron_ingot into the pack of ' $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] iron could not be put into the pack'
    }
    if ($Pack) {
        $modWeapon = @($script:ArmyWeapons | Where-Object { $_ -and -not $_.StartsWith('minecraft:') }) | Select-Object -First 1
        if ($modWeapon) {
            Send-ServerCommand $server "kingdomworld admin companions pack $($script:CompanionOwner) minecraft:iron_sword 1"
            Send-ServerCommand $server "kingdomworld admin companions pack $($script:CompanionOwner) $modWeapon 1"
            [void](Wait-ForLog $server "Put 1 of $([regex]::Escape($modWeapon)) into the pack of " $CommandTimeout $ConsoleFailPattern)
            $seen = Count-Log 'Gear mainhand: (\S+)'
            Send-ServerCommand $server "kingdomworld admin companions gear $($script:CompanionOwner)"
            $hand = Wait-ForNewLog $server 'Gear mainhand: (\S+)' $CommandTimeout $seen
            if ($hand -and $hand.Groups[1].Value -eq $modWeapon) {
                $script:CompanionWeapon = $modWeapon
                Write-Ok "[session1] the companion takes $modWeapon of the pack over an iron sword"
            } else {
                Add-Failure "[session1] the companion did not take $modWeapon (took $(if ($hand) { $hand.Groups[1].Value } else { 'nothing' }))"
            }
        } else {
            Add-Warning '[session1] no weapon of a mod was found for the companion'
        }
    }
    Send-ServerCommand $server "kingdomworld admin companions party add $($script:CompanionOwner) $($script:CompanionMember)"
    if (Wait-ForLog $server 'joined the party of .* \(#\d+\)' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] a party of two was formed'
    } else {
        Add-Failure '[session1] the party could not be formed'
    }
    $seen = Count-Log ', level (\d+), loyalty (\d+), food (\d+), health (\d+), .*, items (\d+)'
    Send-ServerCommand $server "kingdomworld admin companions list $($script:CompanionOwner)"
    $entry = Wait-ForNewLog $server ', level (\d+), loyalty (\d+), food (\d+), health (\d+), .*, items (\d+)' $CommandTimeout $seen
    if ($entry) {
        $script:CompanionEntry = "$($entry.Groups[1].Value)/$($entry.Groups[2].Value)/$($entry.Groups[3].Value)/$($entry.Groups[4].Value)/$($entry.Groups[5].Value)"
        Write-Ok "[session1] the companion: level/loyalty/food/health/items $($script:CompanionEntry)"
    } else {
        Add-Failure '[session1] the companion was not listed'
    }

    # Law: a thief who is offline is wanted for a theft of 120, pays the fine of 240 from an account of 100 and owes the rest
    # to the treasury; another stays wanted for a blow; a murderer is executed (a stand-in with a sword, bread and a
    # chestplate): every item goes into the record of the kingdom, nothing stays with the body, and with -Pack Jack's
    # Gravestones makes no grave. The money of the kingdom only grows by what an operator gave.
    Send-ServerCommand $server 'kingdomworld admin law status'
    if (Wait-ForLog $server 'Law data: 0 wanted, 0 confiscation\(s\), 0 court\(s\), 0 report\(s\) on the way, format 1' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the law data is writable and empty'
    } else {
        Add-Failure '[session1] the law data is not writable or not empty'
    }
    $lawBefore = Get-KingdomMoney $server $KingdomName
    Send-ServerCommand $server "kingdomworld admin law wanted add $($script:LawThief) $KingdomName theft 120"
    [void](Wait-ForLog $server ' is wanted in .* for theft' $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin companions fund $($script:LawThief) $KingdomName 100"
    [void](Wait-ForLog $server 'Account of .* in the currency: \+' $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin law fine $($script:LawThief) $KingdomName pay"
    $paid = Wait-ForLog $server ' paid [^:]+: (\d+) in coins, (\d+) from the account, (\d+) as a debt' $CommandTimeout $ConsoleFailPattern
    if ($paid -and $paid.Groups[1].Value -eq '0' -and $paid.Groups[2].Value -eq '100' -and $paid.Groups[3].Value -eq '140') {
        Write-Ok '[session1] the thief paid the fine of 240: 100 from the account, 140 as a debt'
    } else {
        Add-Failure "[session1] the fine was not paid as expected: $(if ($paid) { $paid.Value } else { 'no answer' })"
    }
    $seen = Count-Log 'Debt of \S+ to \S+: .* \((\d+)\)'
    Send-ServerCommand $server "kingdomworld admin law debt $($script:LawThief) $KingdomName"
    $debt = Wait-ForNewLog $server 'Debt of \S+ to \S+: .* \((\d+)\)' $CommandTimeout $seen
    if ($debt -and $debt.Groups[1].Value -eq '140') {
        $script:LawDebt = 140
        Write-Ok '[session1] the debt to the treasury is 140'
    } else {
        Add-Failure "[session1] the debt is not 140: $(if ($debt) { $debt.Value } else { 'no answer' })"
    }
    $seen = Count-Log 'Reputation of \S+ in \S+: (-?\d+)'
    Send-ServerCommand $server "kingdomworld admin law reputation $($script:LawThief) $KingdomName"
    $reputation = Wait-ForNewLog $server 'Reputation of \S+ in \S+: (-?\d+)' $CommandTimeout $seen
    if ($reputation -and [int]$reputation.Groups[1].Value -lt 0) {
        $script:LawReputation = $reputation.Groups[1].Value
        Write-Ok "[session1] the theft cost reputation: $($script:LawReputation)"
    } else {
        Add-Failure '[session1] the theft did not lower the reputation'
    }
    Send-ServerCommand $server "kingdomworld admin law wanted add $($script:LawWanted) $KingdomName assault"
    [void](Wait-ForLog $server ' is wanted in .* for assault' $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin law pass $($script:LawThief) $KingdomName grant"
    [void](Wait-ForLog $server 'Pass of \S+ in \S+: \d+ tick' $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin law execute $($script:LawOutlaw) $KingdomName kit"
    $executed = Wait-ForLog $server ' executed by the law of \S+: confiscation #(\d+), (\d+) item\(s\) worth (\d+), (\d+) item\(s\) left with the body' $CommandTimeout $ConsoleFailPattern
    if ($executed -and $executed.Groups[2].Value -eq '18' -and $executed.Groups[4].Value -eq '0') {
        $script:LawConfiscation = $executed.Groups[1].Value
        $script:LawGoods = "$($executed.Groups[2].Value)/$($executed.Groups[3].Value)"
        Write-Ok "[session1] the executed murderer lost 18 items into confiscation #$($script:LawConfiscation), none stayed with the body"
    } else {
        Add-Failure "[session1] the execution did not confiscate the 18 items: $(if ($executed) { $executed.Value } else { 'no answer' })"
    }
    if ($Pack) {
        # The grave of a death appears a moment later; the throne lies at 8 70 8 and the stand-in stood next to it.
        Start-Sleep -Seconds 3
        $seen = Count-Log 'Successfully filled|No blocks were filled|Unknown block type'
        Send-ServerCommand $server 'fill 4 66 4 12 76 12 minecraft:air replace jacksgravestones:gravestone'
        $grave = Wait-ForNewLog $server 'Successfully filled (\d+)|No blocks were filled|Unknown block type' $CommandTimeout $seen
        if ($grave -and $grave.Value -like 'No blocks were filled*') {
            Write-Ok "[session1] Jack's Gravestones made no grave of the executed murderer"
        } elseif ($grave -and $grave.Value -like 'Unknown block type*') {
            Add-Warning "[session1] Jack's Gravestones is not in the pack"
        } else {
            Add-Failure "[session1] a grave appeared after the execution: $(if ($grave) { $grave.Value } else { 'no answer' })"
        }
    }
    $lawAfter = Get-KingdomMoney $server $KingdomName
    if ($lawBefore -ge 0 -and $lawAfter -eq $lawBefore + 100) {
        $script:LawMoney = $lawAfter
        Write-Ok "[session1] the law made no money: $lawBefore + 100 given = $lawAfter"
    } else {
        Add-Failure "[session1] the law changed the money: $lawBefore -> $lawAfter (100 was given)"
    }

    # The life of cities: a tavern on flat ground of chunk [-1, -1] with an innkeeper at its counter is open; its innkeeper
    # tells rumors; a citizen falls ill; the king's festival costs the treasury exactly its price and takes its treat; a fair
    # opens. The food and drink of the tavern come from tags (with the mods of the pack, their tags too).
    $served = Wait-ForLog $server 'KingdomWorld taverns serve (\d+) kind\(s\) of food and (\d+) of drink from the tags, of the mods \[([^\]]*)\]' 10
    if ($served -and [int]$served.Groups[1].Value -ge 14 -and [int]$served.Groups[2].Value -ge 1) {
        Write-Ok "[session1] taverns serve $($served.Groups[1].Value) kinds of food and $($served.Groups[2].Value) of drink from the tags (mods: $($served.Groups[3].Value))"
    } else {
        Add-Failure '[session1] the tags of the food and drink of taverns did not load'
    }
    Send-ServerCommand $server 'kingdomworld admin life status'
    if (Wait-ForLog $server 'Day time \d+ \(\w+\), ill 0, rooms 0, goods of fairs 0' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the life of cities is writable and empty'
    } else {
        Add-Failure '[session1] the life of cities is not writable or not empty'
    }
    Send-ServerCommand $server 'forceload add -16 -16 -1 -1'
    Send-ServerCommand $server 'fill -16 69 -16 -1 69 -1 minecraft:stone'
    Send-ServerCommand $server 'fill -16 70 -16 -1 86 -1 minecraft:air'
    Start-Sleep -Seconds 2
    Send-ServerCommand $server "kingdomworld admin build place $KingdomName kingdomworld:tavern -9 70 -6"
    $tavern = Wait-ForLog $server 'Construction #(\d+) laid out: .* at -9 70 -6, (\d+) steps' $CommandTimeout $ConsoleFailPattern
    if ($tavern) {
        Send-ServerCommand $server "kingdomworld admin build finish $($tavern.Groups[1].Value)"
        $tavernBuilt = Wait-ForLog $server "Construction #$($tavern.Groups[1].Value) finished at once; building #(\d+) is in the register" $CommandTimeout $ConsoleFailPattern
        if ($tavernBuilt) { $script:TavernId = $tavernBuilt.Groups[1].Value }
    }
    if (-not $script:TavernId) {
        Add-Failure '[session1] the tavern could not be built'
    } else {
        Send-ServerCommand $server "kingdomworld admin build role $($script:TavernId)"
        if (Wait-ForLog $server "Building #$($script:TavernId) \(kingdomworld:tavern\): role tavern, state active, level 1" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] the tavern #$($script:TavernId) plays its role"
        } else {
            Add-Failure '[session1] the tavern does not play its role'
        }
        # Two newcomers: the innkeeper, and a citizen who falls ill, so that no worker other checks count on is slowed down.
        $count = $script:CitizenIds.Count + 2
        Send-ServerCommand $server "kingdomworld admin citizens spawn 2 $KingdomName"
        [void](Wait-ForLog $server "Spawned 2 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)
        $all = Get-CitizenList $server $count 'session1'
        if ($all) {
            $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
            $script:CitizenIds = @($all)
            $script:InnkeeperId = $new[0]
            Send-ServerCommand $server 'kingdomworld admin posts set -11 71 -14 profession innkeeper'
            if (-not (Wait-ForLog $server 'Work post at -11 71 -14: Innkeeper, radius \d+' $CommandTimeout 'No work post at -11 71 -14|Unknown or incomplete command')) {
                Add-Failure '[session1] the post at the counter of the tavern was not registered or could not be set'
            }
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:InnkeeperId) innkeeper"
            if (Wait-ForLog $server '\S+ is now: Innkeeper' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session1] citizen $($script:InnkeeperId) became the innkeeper"
            } else {
                Add-Failure '[session1] the innkeeper could not be appointed'
            }
            $runs = Count-Log "Ran 1 simulation step\(s\) of $KingdomName"
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 1"
            [void](Wait-ForNewLog $server "Ran 1 simulation step\(s\) of $KingdomName" $CommandTimeout $runs)
            Send-ServerCommand $server "kingdomworld admin life tavern $KingdomName"
            if (Wait-ForLog $server "Tavern $($script:TavernId) at -?\d+ -?\d+ -?\d+: open" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the tavern is open with its innkeeper at the counter'
            } else {
                Add-Failure '[session1] the tavern is not open'
            }
            Send-ServerCommand $server "kingdomworld admin life rumors $KingdomName"
            $rumor = Wait-ForLog $server '(\(camp \d+\)|tell nothing today|blocks|guild pays|guards are looking|cheap|ask a lot|new kingdom|fair in|festival in)' $CommandTimeout $ConsoleFailPattern
            if ($rumor) {
                Write-Ok "[session1] the innkeeper talks: $($rumor.Value)"
            } else {
                Add-Failure '[session1] the rumors of the tavern did not answer'
            }
            $sick = $new[1]
            Send-ServerCommand $server "kingdomworld admin life sick $sick"
            if (Wait-ForLog $server ' fell ill' $CommandTimeout $ConsoleFailPattern) {
                $script:SickId = $sick
                Write-Ok "[session1] citizen $sick fell ill"
            } else {
                Add-Failure '[session1] the citizen did not fall ill'
            }
        }
    }
    Send-ServerCommand $server "kingdomworld admin storage put minecraft:bread 32 $KingdomName"
    [void](Wait-ForLog $server "Stored 32 of minecraft:bread in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
    $festivalBefore = Get-KingdomMoney $server $KingdomName
    Send-ServerCommand $server "kingdomworld admin life festival $KingdomName"
    $festival = Wait-ForLog $server "$KingdomName holds a festival: (\d+) paid, (\d+) of (\d+) food points of treat" $CommandTimeout $ConsoleFailPattern
    $festivalAfter = Get-KingdomMoney $server $KingdomName
    if ($festival -and $festivalBefore -ge 0 -and $festivalAfter -eq $festivalBefore - [int64]$festival.Groups[1].Value -and
            [int]$festival.Groups[2].Value -ge [int]$festival.Groups[3].Value) {
        Write-Ok "[session1] the festival cost the treasury $($festival.Groups[1].Value) and took its treat; the money adds up"
    } else {
        Add-Failure "[session1] the festival went wrong: $(if ($festival) { $festival.Value } else { 'no answer' }), money $festivalBefore -> $festivalAfter"
    }
    Send-ServerCommand $server "kingdomworld admin life fair $KingdomName"
    if (Wait-ForLog $server 'The fair in \S+ is open' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the fair of the capital opened'
    } else {
        Add-Failure '[session1] the fair did not open'
    }

    # Construction: the templates load, the data pack template needs Create; a builder far from players builds a small
    # house from the storehouse on a flat place of chunk [2, 0], and the restart comes half way.
    Send-ServerCommand $server 'kingdomworld admin build templates'
    $templates = Wait-ForLog $server 'Building templates: (\d+)' $CommandTimeout $ConsoleFailPattern
    if ($templates -and [int]$templates.Groups[1].Value -ge 9) {
        Write-Ok "[session1] $($templates.Groups[1].Value) building templates loaded"
    } else {
        Add-Failure '[session1] the building templates (8 built-in and the data pack one) did not load'
    }
    if (Wait-ForLog $server 'kingdomworld:small_house \(Small house\): 9.9.9, blocks \d+, items \d+, can be built' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the small house can be built'
    } else {
        Add-Failure '[session1] the small house is not listed as buildable'
    }
    $hasCreate = @(Get-ChildItem -LiteralPath (Join-Path $ServerDir 'mods') -Filter 'create-*.jar' -ErrorAction SilentlyContinue).Count -gt 0
    if ($hasCreate) {
        if (Wait-ForLog $server 'smoketest:create_shed \(Create shed\): 3.3.3, blocks \d+, items \d+, can be built' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the data pack template with a block of Create can be built with Create installed'
        } else {
            Add-Failure '[session1] the data pack template with a block of Create is not buildable although Create is installed'
        }
    } else {
        if (Wait-ForLog $server 'smoketest:create_shed \(Create shed\): .*create' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the data pack template that needs Create cannot be built and names the mod'
        } else {
            Add-Failure '[session1] the data pack template that needs Create did not say why it cannot be built'
        }
    }
    Send-ServerCommand $server 'forceload add 32 0 47 15'
    Send-ServerCommand $server 'fill 33 69 1 46 69 15 minecraft:stone'
    Send-ServerCommand $server 'fill 33 70 1 46 85 15 minecraft:air'
    Send-ServerCommand $server 'setblock 33 70 2 kingdomworld:work_post'
    Start-Sleep -Seconds 2
    Send-ServerCommand $server 'kingdomworld admin posts set 33 70 2 profession builder'
    if (-not (Wait-ForLog $server 'Work post at 33 70 2: Builder, radius \d+' $CommandTimeout 'No work post at 33 70 2|Unknown or incomplete command')) {
        Add-Failure '[session1] the builder post was not registered or could not be set'
    }
    $count = $script:CitizenIds.Count + 1
    Send-ServerCommand $server "kingdomworld admin citizens spawn 1 $KingdomName"
    [void](Wait-ForLog $server "Spawned 1 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)
    $all = Get-CitizenList $server $count 'session1'
    if ($all) {
        $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
        $script:CitizenIds = @($all)
        $script:BuilderId = $new[0]
        Send-ServerCommand $server "kingdomworld admin citizens profession $($script:BuilderId) builder"
        if (Wait-ForLog $server '\S+ is now: Builder' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] citizen $($script:BuilderId) became a builder"
        } else {
            Add-Failure '[session1] the builder could not be appointed'
        }
    }
    # Buildings on chunk [-1, 1], away from the posts of the other checks: a well of birch plays its role, loses blocks, is
    # repaired by the builder with materials from the storehouse before the house is laid out, and then demolished, its
    # materials going back with a loss.
    Send-ServerCommand $server 'forceload add -16 16 -1 31'
    Send-ServerCommand $server 'fill -16 69 16 -1 69 31 minecraft:stone'
    Send-ServerCommand $server 'fill -16 70 16 -1 80 31 minecraft:air'
    Start-Sleep -Seconds 2
    $wellId = $null
    Send-ServerCommand $server "kingdomworld admin build place $KingdomName kingdomworld:well -12 70 29 0 false `"minecraft:birch`""
    $well = Wait-ForLog $server 'Construction #(\d+) laid out: .* at -12 70 29, (\d+) steps' $CommandTimeout $ConsoleFailPattern
    if ($well) {
        Send-ServerCommand $server "kingdomworld admin build finish $($well.Groups[1].Value)"
        $wellBuilt = Wait-ForLog $server "Construction #$($well.Groups[1].Value) finished at once; building #(\d+) is in the register" $CommandTimeout $ConsoleFailPattern
        if ($wellBuilt) { $wellId = $wellBuilt.Groups[1].Value }
    }
    if (-not $wellId) {
        Add-Failure '[session1] the well of birch could not be built'
    } else {
        Send-ServerCommand $server "kingdomworld admin build role $wellId"
        if (Wait-ForLog $server "Building #$wellId \(kingdomworld:well\): role well, state active, level 1, .* wood minecraft:birch" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] the well #$wellId plays its role and is built of birch"
        } else {
            Add-Failure '[session1] the well does not play its role or is not of birch'
        }
        Send-ServerCommand $server "kingdomworld admin build damage $wellId 40"
        [void](Wait-ForLog $server "Building #$wellId lost \d+ blocks" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin build check $wellId"
        $checked = Wait-ForLog $server "Building #$wellId looked at: (\d+)% stands" $CommandTimeout $ConsoleFailPattern
        Send-ServerCommand $server "kingdomworld admin build role $wellId"
        if ($checked -and [int]$checked.Groups[1].Value -lt 70 -and
                (Wait-ForLog $server "Building #$wellId \(kingdomworld:well\): role well, state damaged" $CommandTimeout $ConsoleFailPattern)) {
            Write-Ok "[session1] the damaged well ($($checked.Groups[1].Value)%) rests"
        } else {
            Add-Failure '[session1] the damaged well still plays its role'
        }
        Send-ServerCommand $server "kingdomworld admin build repair $wellId"
        $repair = Wait-ForLog $server 'The builders will repair .* \(construction #(\d+)\)' $CommandTimeout $ConsoleFailPattern
        if ($repair) {
            $rid = $repair.Groups[1].Value
            # The house still sets its materials aside while the repair goes first.
            Send-ServerCommand $server "kingdomworld admin build priority $rid high"
            [void](Wait-ForLog $server 'Priority of ' $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin build stock $rid"
            [void](Wait-ForLog $server "Materials for construction #$rid put into the storehouses: \d+, did not fit: 0" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 3"
            [void](Wait-ForLog $server "Ran 3 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server 'kingdomworld admin build tick'
            [void](Wait-ForLog $server 'Constructions moved on: \d+' $CommandTimeout $ConsoleFailPattern)
            $seen = Count-Log "Building #$wellId looked at: (\d+)% stands"
            Send-ServerCommand $server "kingdomworld admin build check $wellId"
            $whole = Wait-ForNewLog $server "Building #$wellId looked at: (\d+)% stands" $CommandTimeout $seen
            $seen = Count-Log "Building #$wellId \(kingdomworld:well\): role well, state active"
            Send-ServerCommand $server "kingdomworld admin build role $wellId"
            $active = Wait-ForNewLog $server "Building #$wellId \(kingdomworld:well\): role well, state active" $CommandTimeout $seen
            if ($whole -and $whole.Groups[1].Value -eq '100' -and $active) {
                Write-Ok '[session1] the builder repaired the well from the storehouse and its role came back'
            } else {
                Add-Failure "[session1] the well was not repaired: $(if ($whole) { $whole.Value } else { 'no check' })"
            }
        } else {
            Add-Failure '[session1] the repair of the well could not be ordered'
        }
        Send-ServerCommand $server "kingdomworld admin build demolish $wellId"
        $demolition = Wait-ForLog $server 'The builders will take .* down \(construction #(\d+)\)' $CommandTimeout $ConsoleFailPattern
        if ($demolition) {
            Send-ServerCommand $server "kingdomworld admin build finish $($demolition.Groups[1].Value)"
            [void](Wait-ForLog $server "Construction #$($demolition.Groups[1].Value) finished at once" $CommandTimeout $ConsoleFailPattern)
            $before = (Get-LatestLog).Length
            Send-ServerCommand $server "kingdomworld admin build buildings $KingdomName"
            Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
            Start-Sleep -Seconds 3
            $listed = (Get-LatestLog).Substring($before)
            if ($listed -notmatch 'kingdomworld:well \(well\)' -and $listed -match 'minecraft:birch_slab: \d+' -and $listed -match 'minecraft:cobblestone: \d+') {
                Write-Ok '[session1] the well was demolished: it left the register and its birch and stone went to the storehouses'
            } else {
                Add-Failure '[session1] the demolition of the well left it in the register or gave nothing back'
            }
        } else {
            Add-Failure '[session1] the demolition of the well could not be ordered'
        }
    }
    Send-ServerCommand $server "kingdomworld admin build place $KingdomName kingdomworld:small_house 39 70 15"
    $laid = Wait-ForLog $server 'Construction #(\d+) laid out: .* at 39 70 15, (\d+) steps' $CommandTimeout $ConsoleFailPattern
    if ($laid) {
        $script:ConstructionId = $laid.Groups[1].Value
        Write-Ok "[session1] construction #$($script:ConstructionId) of a small house laid out with $($laid.Groups[2].Value) steps"
    } else {
        Add-Failure '[session1] the small house could not be laid out'
    }
    if ($script:ConstructionId) {
        $id = $script:ConstructionId
        Send-ServerCommand $server "kingdomworld admin build stock $id"
        if (Wait-ForLog $server "Materials for construction #$id put into the storehouses: (\d+), did not fit: 0" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the materials of the house are in the storehouse'
        } else {
            Add-Failure '[session1] the materials of the house did not fit into the storehouse'
        }
        Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 4"
        [void](Wait-ForLog $server "Ran 4 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server 'kingdomworld admin build tick'
        [void](Wait-ForLog $server 'Constructions moved on: \d+' $CommandTimeout $ConsoleFailPattern)
        $infoPattern = "#$id kingdomworld:small_house at 39 70 15: steps done (\d+) of (\d+), blocks placed (\d+) of (\d+), paid and waiting (\d+)"
        $seen = Count-Log $infoPattern
        Send-ServerCommand $server "kingdomworld admin build info $id"
        $info = Wait-ForNewLog $server $infoPattern $CommandTimeout $seen
        if ($info -and [int]$info.Groups[3].Value -gt 0 -and [int]$info.Groups[3].Value -lt [int]$info.Groups[4].Value) {
            $script:ConstructionInfo = "$($info.Groups[1].Value)/$($info.Groups[2].Value)/$($info.Groups[3].Value)/$($info.Groups[4].Value)/$($info.Groups[5].Value)"
            Write-Ok "[session1] the builder built half way far from players: $($info.Groups[3].Value) of $($info.Groups[4].Value) blocks placed"
        } else {
            Add-Failure "[session1] the house did not grow half way: '$($info.Value)'"
        }
        if ($hasCreate) {
            Send-ServerCommand $server "kingdomworld admin build place $KingdomName smoketest:create_shed 45 70 6"
            $shed = Wait-ForLog $server 'Construction #(\d+) laid out: .* at 45 70 6' $CommandTimeout $ConsoleFailPattern
            if ($shed) {
                Send-ServerCommand $server "kingdomworld admin build finish $($shed.Groups[1].Value)"
                if (Wait-ForLog $server "Construction #$($shed.Groups[1].Value) finished at once; building #(\d+) is in the register" $CommandTimeout $ConsoleFailPattern) {
                    Write-Ok '[session1] the shed with blocks of Create was built'
                } else {
                    Add-Failure '[session1] the shed with blocks of Create was not finished'
                }
            } else {
                Add-Failure '[session1] the shed with blocks of Create could not be laid out'
            }
        }
    }

    # A hut that stands on chunk [-1, 1] is saved as a blueprint of the kingdom, and a construction from it waits paused
    # over the restart. With -Pack a house is built of a wood of a mod of the pack.
    Send-ServerCommand $server 'fill -15 70 17 -13 70 19 minecraft:cobblestone'
    Send-ServerCommand $server 'setblock -14 71 19 minecraft:red_bed[facing=north,part=foot]'
    Send-ServerCommand $server 'setblock -14 71 18 minecraft:red_bed[facing=north,part=head]'
    Send-ServerCommand $server 'setblock -15 71 17 minecraft:oak_planks'
    Send-ServerCommand $server 'setblock -13 71 17 minecraft:oak_planks'
    Start-Sleep -Seconds 1
    Send-ServerCommand $server "kingdomworld admin build capture $KingdomName -15 70 17 -13 72 19 `"Smoke hut`" house housing"
    if (Wait-ForLog $server 'Blueprint .Smoke hut. saved \(3 x 3 x 3\)' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the hut was saved as a blueprint of the kingdom'
    } else {
        Add-Failure '[session1] the hut could not be saved as a blueprint'
    }
    Send-ServerCommand $server "kingdomworld admin build blueprints $KingdomName"
    $saved = Wait-ForLog $server "(kingdomworld:custom/\d+) of ${KingdomName}: Smoke hut, role house, 3 x 3 x 3" $CommandTimeout $ConsoleFailPattern
    if ($saved) {
        $script:HutTemplate = $saved.Groups[1].Value
        Send-ServerCommand $server "kingdomworld admin build place $KingdomName $($script:HutTemplate) -7 70 21"
        $hut = Wait-ForLog $server 'Construction #(\d+) laid out: .* at -7 70 21, (\d+) steps' $CommandTimeout $ConsoleFailPattern
        if ($hut) {
            $script:HutId = $hut.Groups[1].Value
            # Paused at once: builders would finish a hut of a dozen blocks before the restart, which waits for it.
            Send-ServerCommand $server "kingdomworld admin build pause $($script:HutId)"
            [void](Wait-ForLog $server "Construction #$($script:HutId) paused" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin build stock $($script:HutId)"
            [void](Wait-ForLog $server "Materials for construction #$($script:HutId) put into the storehouses: \d+, did not fit: 0" $CommandTimeout $ConsoleFailPattern)
        } else {
            Add-Failure '[session1] no construction could be laid out from the saved blueprint'
        }
    } else {
        Add-Failure '[session1] the saved blueprint is not listed'
    }
    if ($Pack) {
        Send-ServerCommand $server 'kingdomworld admin build materials'
        $materials = Wait-ForLog $server 'Woods \((\d+)\): ([^;]*); stones' $CommandTimeout $ConsoleFailPattern
        $modWood = if ($materials) { @($materials.Groups[2].Value -split ', ' | Where-Object { $_ -and $_ -notlike 'minecraft:*' })[0] } else { $null }
        if ($modWood) {
            Send-ServerCommand $server "kingdomworld admin build place $KingdomName kingdomworld:small_house -5 70 31 0 false `"$modWood`""
            $woodHouse = Wait-ForLog $server 'Construction #(\d+) laid out: .* at -5 70 31, (\d+) steps' $CommandTimeout $ConsoleFailPattern
            if ($woodHouse) {
                $wid = $woodHouse.Groups[1].Value
                Send-ServerCommand $server "kingdomworld admin build info $wid"
                $planks = Wait-ForLog $server "still to pay: .*$([regex]::Escape($modWood))_planks x\d+" $CommandTimeout $ConsoleFailPattern
                Send-ServerCommand $server "kingdomworld admin build finish $wid"
                $built = Wait-ForLog $server "Construction #$wid finished at once; building #(\d+) is in the register" $CommandTimeout $ConsoleFailPattern
                $whole = $null
                if ($built) {
                    Send-ServerCommand $server "kingdomworld admin build check $($built.Groups[1].Value)"
                    $whole = Wait-ForLog $server "Building #$($built.Groups[1].Value) looked at: 100% stands" $CommandTimeout $ConsoleFailPattern
                }
                if ($planks -and $whole) {
                    Write-Ok "[session1] a small house was built of $modWood, a wood of the pack: its cost names its planks and it stands whole"
                } else {
                    Add-Failure "[session1] the small house of $modWood was not built of that wood"
                }
            } else {
                Add-Failure "[session1] a small house of $modWood could not be laid out"
            }
        } else {
            Add-Warning '[session1] the pack has no wood of a mod named the usual way; the wood of mods was not checked'
        }
    }

    # The builder would go on while the server runs, and a simulation step may come between a reading and the stop: the
    # house and the hut are paused together right before the stop and their paid blocks placed, so the numbers taken last
    # stay as they are until the restart, and nothing they need is free for the workshops for long.
    if ($script:ConstructionId) {
        $id = $script:ConstructionId
        $infoPattern = "#$id kingdomworld:small_house at 39 70 15: steps done (\d+) of (\d+), blocks placed (\d+) of (\d+), paid and waiting (\d+)"
        Send-ServerCommand $server "kingdomworld admin build pause $id"
        if (-not (Wait-ForLog $server "Construction #$id paused" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the house could not be paused before the restart'
        }
        Send-ServerCommand $server 'kingdomworld admin build tick'
        [void](Wait-ForLog $server 'Constructions moved on: \d+' $CommandTimeout $ConsoleFailPattern)
        $seen = Count-Log $infoPattern
        Send-ServerCommand $server "kingdomworld admin build info $id"
        $last = Wait-ForNewLog $server $infoPattern $CommandTimeout $seen
        if ($last) {
            $script:ConstructionInfo = "$($last.Groups[1].Value)/$($last.Groups[2].Value)/$($last.Groups[3].Value)/$($last.Groups[4].Value)/$($last.Groups[5].Value)"
        }
    }
    if ($script:HutId) {
        Send-ServerCommand $server "kingdomworld admin build pause $($script:HutId)"
        [void](Wait-ForLog $server "Construction #$($script:HutId) paused" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server 'kingdomworld admin build tick'
        [void](Wait-ForLog $server 'Constructions moved on: \d+' $CommandTimeout $ConsoleFailPattern)
        $hutPattern = "#$($script:HutId) $([regex]::Escape($script:HutTemplate)) at -7 70 21: steps done (\d+) of (\d+), blocks placed (\d+) of (\d+), paid and waiting (\d+)"
        $seen = Count-Log $hutPattern
        Send-ServerCommand $server "kingdomworld admin build info $($script:HutId)"
        $hutInfo = Wait-ForNewLog $server $hutPattern $CommandTimeout $seen
        if ($hutInfo) {
            $script:HutInfo = "$($hutInfo.Groups[1].Value)/$($hutInfo.Groups[2].Value)/$($hutInfo.Groups[3].Value)/$($hutInfo.Groups[4].Value)/$($hutInfo.Groups[5].Value)"
            Write-Ok "[session1] a construction #$($script:HutId) from the saved blueprint waits over the restart: $($script:HutInfo)"
        } else {
            Add-Failure '[session1] the construction from the saved blueprint shows no numbers'
        }
    }

    # Money last: the simulation steps of the construction let the minter strike what the smelter made meanwhile, so the
    # treasury and the money of both kingdoms to compare after the restart are read right before the stop.
    if ($script:EconomyRaw) {
        $seen = Count-Log 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)'
        Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
        $raw = Wait-ForNewLog $server 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)' $CommandTimeout $seen
        if ($raw) { $script:EconomyRaw = $raw.Value }
    }
    if ($script:TradeMoney -or $script:GuildMoney -ge 0) {
        $moneyNow = Get-KingdomMoney $server $KingdomName
        if ($script:GuildMoney -ge 0) { $script:GuildMoney = $moneyNow }
        if ($script:TradeMoney) { $script:TradeMoney = "$moneyNow/$(Get-KingdomMoney $server $PartnerName)" }
    }
}

$dataFile = Join-Path $ServerDir 'world\data\kingdomworld_kingdoms.dat'
if (Test-Path -LiteralPath $dataFile) { Write-Ok "saved data written: $dataFile" } else { Add-Failure "saved data file missing: $dataFile" }
$serverConfig = Join-Path $ServerDir 'world\serverconfig\kingdomworld-server.toml'
if (Test-Path -LiteralPath $serverConfig) { Write-Ok 'per-world server config created' } else { Add-Failure "server config missing: $serverConfig" }

if (-not $script:CreatedId) { Stop-Smoke 'kingdom was not created, skipping the restart check' }

Invoke-Phase 'session2' {
    param($server)
    $citizenCount = $script:CitizenIds.Count
    $postCount = 1
    if ($script:SmithId) { $postCount += 2 }
    if ($script:MinterId) { $postCount += 1 }
    if ($script:BuilderId) { $postCount += 1 }
    if ($script:InnkeeperId) { $postCount += 1 }
    if (-not (Wait-ForLog $server "KingdomWorld loaded 2 kingdom\(s\) with $citizenCount citizen\(s\), $postCount work post\(s\), 2 storehouse\(s\), 2 treasury\(ies\), \d+ player\(s\) with accounts, 1 market\(s\), 1 deal\(s\), 1 caravan\(s\) on the way" 10)) {
        Add-Failure "[session2] expected 2 kingdoms with $citizenCount citizens, $postCount work post(s), 2 storehouses, 2 treasuries, a market, a deal and a caravan on the way to be loaded after restart"
    }
    if ($MapModsDir -and $script:CaravanId) { Test-MapMarkers $server 'session2' }
    Send-ServerCommand $server 'kingdomworld debug list'
    $listed = Wait-ForLog $server " - $KingdomName \(id ($IdPattern)\)" $CommandTimeout 'Unknown or incomplete command|There are no kingdoms yet'
    if (-not $listed) {
        Add-Failure "[session2] $KingdomName is missing from 'kingdomworld debug list' after restart"
    } elseif ($listed.Groups[1].Value -ne $script:CreatedId) {
        Add-Failure "[session2] $KingdomName has id $($listed.Groups[1].Value) after restart, expected $($script:CreatedId)"
    } else {
        Write-Ok "[session2] $KingdomName survived the restart with the same id"
    }
    if ($script:Magic) {
        $magic = Get-KingdomMagic $server $KingdomName
        if ($magic -and $magic.Groups[1].Value -eq $script:Magic.Groups[1].Value -and $magic.Groups[3].Value -eq $script:Magic.Groups[3].Value `
                -and $magic.Groups[5].Value -eq $script:Magic.Groups[5].Value -and $magic.Groups[6].Value -eq $script:Magic.Groups[6].Value `
                -and $magic.Groups[7].Value -eq $script:Magic.Groups[7].Value) {
            Write-Ok "[session2] energy, crystals, research and spells survived the restart: energy $($magic.Groups[1].Value), crystals $($magic.Groups[3].Value)"
        } else {
            Add-Failure "[session2] magic changed in the restart: $($script:Magic.Value) -> $($magic.Value)"
        }
        if ((Get-LatestLog) -match 'KingdomWorld magic: 1 kingdom\(s\) with \d+ energy in all') {
            Write-Ok '[session2] the magic data was loaded at startup'
        } else {
            Add-Failure '[session2] the magic data did not report itself at startup'
        }
    }
    Send-ServerCommand $server "kingdomworld debug info $KingdomName"
    if (Wait-ForLog $server "Id: $([regex]::Escape($script:CreatedId))" $CommandTimeout 'There is no kingdom') {
        Write-Ok "[session2] 'kingdomworld debug info $KingdomName' works"
    } else {
        Add-Failure "[session2] 'kingdomworld debug info $KingdomName' did not show the kingdom"
    }
    foreach ($check in @(@('2 0', 'belongs to SmokeTest'), @('0 0', 'belongs to SmokeTest'), @('-1 -1', 'belongs to SmokeTest'), @('3 0', 'is unclaimed'))) {
        $coords = $check[0]
        $label = "[$($coords -replace ' ', ', ')]"
        Send-ServerCommand $server "kingdomworld admin owner $coords"
        if (Wait-ForLog $server "Chunk $([regex]::Escape($label)) in minecraft:overworld $($check[1])" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] chunk $label $($check[1])"
        } else {
            Add-Failure "[session2] chunk ${label}: expected '$($check[1])' after restart"
        }
    }
    Send-ServerCommand $server "kingdom info $KingdomName"
    if (Wait-ForLog $server 'Chunks: 10 of \d+' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok "[session2] 'kingdom info' reports all 10 chunks after restart"
    } else {
        Add-Failure "[session2] 'kingdom info $KingdomName' did not report 10 chunks"
    }
    $ids = Get-CitizenList $server $citizenCount 'session2'
    if ($ids) {
        $expected = @($script:CitizenIds | Sort-Object)
        $actual = @($ids | Sort-Object)
        if (($expected -join ',') -eq ($actual -join ',')) {
            Write-Ok "[session2] the same $citizenCount citizens survived the restart: $($actual -join ', ')"
        } else {
            Add-Failure "[session2] citizens after restart are $($actual -join ', '), expected $($expected -join ', ')"
        }
        $first = $ids[0]
        Send-ServerCommand $server "kingdomworld admin citizens info $first"
        if (Wait-ForLog $server "Kingdom: $KingdomName" $CommandTimeout 'No citizen has an id') {
            Write-Ok "[session2] 'kingdomworld admin citizens info $first' works"
        } else {
            Add-Failure "[session2] 'kingdomworld admin citizens info $first' did not show the citizen"
        }
    }
    if ($script:MinerId) {
        Send-ServerCommand $server "kingdomworld admin posts list $KingdomName"
        $post = Wait-ForLog $server ' - 12 40 12: Miner, workers 1/1, radius \d+, (.+), today (\d+)' $CommandTimeout $ConsoleFailPattern
        if ($post) {
            Write-Ok '[session2] the work post kept its profession and its miner after restart'
        } else {
            Add-Failure '[session2] the work post lost its settings or its miner after restart'
        }
        Send-ServerCommand $server "kingdomworld admin citizens info $($script:MinerId)"
        if (Wait-ForLog $server 'Workplace: 12 40 12' $CommandTimeout 'No citizen has an id') {
            Write-Ok "[session2] the miner $($script:MinerId) still works at the post"
        } else {
            Add-Failure "[session2] the miner $($script:MinerId) lost the workplace after restart"
        }
        Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
        $stored = Wait-ForLog $server "Storehouses of ${KingdomName}: (\d+) kinds of items in 1 storehouses" $CommandTimeout $ConsoleFailPattern
        if ($stored -and [int]$stored.Groups[1].Value -ge 2) {
            Write-Ok '[session2] the contents of the storehouse survived the restart'
        } else {
            Add-Failure '[session2] the storehouse lost its contents after restart'
        }
    }
    if ($script:SwordMade) {
        if (Wait-ForLog $server ' - minecraft:iron_sword: 1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the iron sword is still in the storehouse'
        } else {
            Add-Failure '[session2] the iron sword was lost after restart'
        }
    }
    if ($script:KeepOrderId) {
        Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
        if (Wait-ForLog $server " - #$($script:KeepOrderId) minecraft:bread \(.+\): keep 5" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] the keep order #$($script:KeepOrderId) survived the restart"
        } else {
            Add-Failure "[session2] the keep order #$($script:KeepOrderId) was lost after restart"
        }
        Send-ServerCommand $server 'kingdomworld admin posts list SmokeTest'
        if (Wait-ForLog $server ' - 24 70 12: Smith, workers 1/1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the smithy kept its smith after restart'
        } else {
            Add-Failure '[session2] the smithy lost its smith after restart'
        }
    }
    if ($script:EconomyRaw) {
        # The treasury and its coins are exactly as they were: nothing lost, nothing made by the restart.
        Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
        $raw = Wait-ForLog $server 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)' $CommandTimeout $ConsoleFailPattern
        if ($raw -and $raw.Value -eq $script:EconomyRaw) {
            Write-Ok "[session2] the treasury survived the restart unchanged: $($raw.Value)"
        } else {
            Add-Failure "[session2] the treasury changed in the restart: '$(if ($raw) { $raw.Value })', expected '$($script:EconomyRaw)'"
        }
        Send-ServerCommand $server 'kingdomworld admin posts list SmokeTest'
        if (Wait-ForLog $server ' - 24 70 20: Minter, workers 1/1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the mint post kept its minter after restart'
        } else {
            Add-Failure '[session2] the mint post lost its minter after restart'
        }
    }
    if ($script:WallRow) {
        # The wall after the restart: the same columns, gates and number in the register.
        Send-ServerCommand $server 'kingdomworld admin roads wall list'
        if (Wait-ForLog $server ([regex]::Escape($script:WallRow)) $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] wall #$($script:WallId) survived the restart: $($script:WallRow)"
        } else {
            Add-Failure "[session2] wall #$($script:WallId) changed in the restart"
        }
    }
    if ($script:CaravanId) {
        # Roads after the restart: the city, the road with the same blocks paid, the camp and the way of the caravan.
        if ($script:RoadId) {
            Send-ServerCommand $server 'kingdomworld admin roads status'
            if (Wait-ForLog $server 'Roads: 1 city, 1 road, camps: 1, caravans on roads: 1, data format 1' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session2] the city, the road, the camp and the way of the caravan survived the restart'
            } else {
                Add-Failure '[session2] the roads did not survive the restart'
            }
            Send-ServerCommand $server 'kingdomworld admin roads city list'
            if (Wait-ForLog $server "#1 Smoke Hollow of $KingdomName at 40 100 8" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session2] the city Smoke Hollow is in its place'
            } else {
                Add-Failure '[session2] the city Smoke Hollow was lost'
            }
            Send-ServerCommand $server 'kingdomworld admin roads road list'
            if (Wait-ForLog $server "#$($script:RoadId) path, \w+, \d+ blocks, paid $($script:RoadPaid) of $($script:RoadUnits), condition [0-9.]+, $KingdomName, own" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session2] road #$($script:RoadId) kept $($script:RoadPaid) of $($script:RoadUnits) paid blocks"
            } else {
                Add-Failure "[session2] road #$($script:RoadId) changed in the restart"
            }
            Send-ServerCommand $server 'kingdomworld admin roads camp list'
            if (Wait-ForLog $server '#1 at -3000 \d+ -3000: 5 robbers and a leader, loot 0, raids 0' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session2] the camp of robbers is the same'
            } else {
                Add-Failure '[session2] the camp of robbers changed in the restart'
            }
            if ($script:CaravanWay) {
                Send-ServerCommand $server 'kingdomworld admin roads caravans'
                if (Wait-ForLog $server "Caravan #$($script:CaravanId) \(deal #\d+\) traveling at -?\d+ -?\d+: $([regex]::Escape($script:CaravanWay)), 32 items" $CommandTimeout $ConsoleFailPattern) {
                    Write-Ok "[session2] the caravan keeps its way on the road: $($script:CaravanWay)"
                } else {
                    Add-Failure '[session2] the way of the caravan changed in the restart'
                }
            }
        }

        # The caravan is still on its way after the restart, with the same cargo and payment; brought in now, it delivers
        # the wheat and pays the seller, and the money of both currencies stays what it was.
        Send-ServerCommand $server 'kingdomworld admin trade caravans'
        if (Wait-ForLog $server "#$($script:CaravanId) $KingdomName -> ${PartnerName}: minecraft:wheat x32, payment 100, traveling, (\d+) ticks left" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] caravan #$($script:CaravanId) survived the restart on its way"
        } else {
            Add-Failure "[session2] caravan #$($script:CaravanId) did not survive the restart"
        }
        Send-ServerCommand $server 'kingdomworld admin trade arrive'
        if (Wait-ForLog $server 'Caravans arrived: 1, still on the way: 0' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the caravan arrived'
        } else {
            Add-Failure '[session2] the caravan did not arrive'
        }
        Send-ServerCommand $server "kingdomworld admin storage list $PartnerName"
        if (Wait-ForLog $server ' - (minecraft:wheat|Wheat): 32' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] the wheat is in the storehouse of $PartnerName"
        } else {
            Add-Failure "[session2] the wheat did not reach the storehouse of $PartnerName"
        }
        $ratePattern = '(\S+): rate (\d+), money (\d+), coins in hands (\d+), treasury (\d+), reserves (\d+), exported today (\d+)'
        $seen = Count-Log $ratePattern
        Send-ServerCommand $server 'kingdomworld admin trade rates'
        [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
        Start-Sleep -Seconds 1
        $log = Get-LatestLog
        $mine = [regex]::Matches($log, "${KingdomName}: rate \d+, money (\d+), coins in hands \d+, treasury \d+, reserves (\d+)")
        $theirs = [regex]::Matches($log, "${PartnerName}: rate \d+, money (\d+),")
        $now = if ($mine.Count -gt 0 -and $theirs.Count -gt 0) { "$($mine[$mine.Count - 1].Groups[1].Value)/$($theirs[$theirs.Count - 1].Groups[1].Value)" } else { '' }
        if ($now -eq $script:TradeMoney -and $mine[$mine.Count - 1].Groups[2].Value -eq '1') {
            Write-Ok "[session2] the delivery made no money: $now, the seller holds the buyer's currency in its reserves"
        } else {
            Add-Failure "[session2] money changed in the delivery: '$now', expected '$($script:TradeMoney)' and a reserve"
        }
    }

    # The guild after the restart: the squad is still away on its contract, both contracts and their escrow are there.
    # Back home, the squad finishes or brings the contract back; either way no money and no more crystals than monsters appear.
    if ($script:GuildSquad) {
        Send-ServerCommand $server "kingdomworld admin guild squad list $KingdomName"
        if (Wait-ForLog $server "#$($script:GuildSquad) of ${KingdomName}: members 2, rank \w+, away, contract #$($script:GuildHunt)," $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] squad #$($script:GuildSquad) is still away on contract #$($script:GuildHunt)"
        } else {
            Add-Failure '[session2] the squad on its expedition did not survive the restart'
        }
        Send-ServerCommand $server "kingdomworld admin guild contract list $KingdomName"
        $huntLine = Wait-ForLog $server "#$($script:GuildHunt) hunt at ${KingdomName}: minecraft:zombie 0/3, reward 100, status taken, taker squad $($script:GuildSquad)" $CommandTimeout $ConsoleFailPattern
        $bountyLine = Wait-ForLog $server "#$($script:GuildBounty) bounty at ${KingdomName}: minecraft:ravager 0/1, reward 50, status open" $CommandTimeout $ConsoleFailPattern
        if ($huntLine -and $bountyLine) {
            Write-Ok '[session2] both contracts survived the restart'
        } else {
            Add-Failure '[session2] the contracts of the guild did not survive the restart'
        }
        $seen = Count-Log 'Held by the guild in the currency of'
        Send-ServerCommand $server 'kingdomworld admin guild money'
        $held = Wait-ForNewLog $server "Held by the guild in the currency of ${KingdomName}: (\d+)," $CommandTimeout $seen
        if ($held -and $held.Groups[1].Value -eq '150') {
            Write-Ok '[session2] the escrow of 150 survived the restart'
        } else {
            Add-Failure "[session2] the escrow changed in the restart: $($held.Value)"
        }
        $before = Get-KingdomMoney $server $KingdomName
        if ($before -eq $script:GuildMoney) {
            Write-Ok "[session2] the money of $KingdomName is the same after the restart: $before"
        } else {
            Add-Failure "[session2] the money of $KingdomName changed in the restart: $($script:GuildMoney) -> $before"
        }
        Send-ServerCommand $server 'kingdomworld admin guild squad return'
        if (Wait-ForLog $server 'Squads back: 1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the squad came back'
        } else {
            Add-Failure '[session2] the squad did not come back'
        }
        $after = Get-KingdomMoney $server $KingdomName
        if ($after -eq $before) {
            Write-Ok "[session2] the expedition made no money: $after"
        } else {
            Add-Failure "[session2] the expedition changed the money: $before -> $after"
        }
        $crystals = Get-KingdomCrystals $server $KingdomName
        if ($crystals -and [int]$crystals.Groups[1].Value -le 3) {
            Write-Ok "[session2] crystals from the expedition: $($crystals.Groups[1].Value) of at most 3 monsters, energy $($crystals.Groups[2].Value)"
        } else {
            Add-Failure "[session2] more crystals than monsters killed: $($crystals.Value)"
        }
    }
    # The army after the restart: the soldiers with their weapons, the squad on its point and the hired band are all there,
    # nobody twice. Dismissed soldiers bring their weapons back to the storehouses, exactly one of each.
    if ($script:ArmySquad) {
        Send-ServerCommand $server "kingdomworld admin army squad list $KingdomName"
        if (Wait-ForLog $server "#$($script:ArmySquad) of ${KingdomName}: soldiers $($script:ArmySoldiers), order hold at 20 70 20" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] squad #$($script:ArmySquad) still holds its point"
        } else {
            Add-Failure '[session2] the squad and its order did not survive the restart'
        }
        if ($script:ArmyBand) {
            if (Wait-ForLog $server "#$($script:ArmyBandSquad) of ${KingdomName}: mercenaries \d+, order none" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session2] the squad of the mercenaries survived the restart"
            } else {
                Add-Failure '[session2] the squad of the mercenaries did not survive the restart'
            }
            Send-ServerCommand $server "kingdomworld admin army mercenary list $KingdomName"
            if (Wait-ForLog $server "Band #$($script:ArmyBand) of ${KingdomName}: \d+ fighter\(s\), squad #$($script:ArmyBandSquad), paid until day $($script:ArmyPaidUntil)," $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session2] band #$($script:ArmyBand) is still hired and paid until day $($script:ArmyPaidUntil)"
            } else {
                Add-Failure '[session2] the hired band did not survive the restart'
            }
        }
        $soldierPattern = "Soldiers of ${KingdomName}: (\d+)"
        $seen = Count-Log $soldierPattern
        Send-ServerCommand $server "kingdomworld admin army soldiers $KingdomName"
        $soldiers = Wait-ForNewLog $server $soldierPattern $CommandTimeout $seen
        Start-Sleep -Seconds 1
        $log = Get-LatestLog
        if ($soldiers -and [int]$soldiers.Groups[1].Value -eq $script:ArmySoldiers) {
            Write-Ok "[session2] all $($script:ArmySoldiers) soldiers survived the restart"
        } else {
            Add-Failure "[session2] the soldiers changed in the restart: '$(if ($soldiers) { $soldiers.Value })'"
        }
        foreach ($weapon in $script:ArmyWeapons) {
            if ($log -notmatch "weapon $([regex]::Escape($weapon)), armor") {
                Add-Failure "[session2] no soldier holds $weapon after the restart"
            }
        }
        $before = Get-KingdomMoney $server $KingdomName
        Send-ServerCommand $server "kingdomworld admin army dismiss $KingdomName"
        if (Wait-ForLog $server "Dismissed $($script:ArmySoldiers) soldier\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] the soldiers were dismissed"
        } else {
            Add-Failure '[session2] the soldiers were not dismissed'
        }
        Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
        Start-Sleep -Seconds 2
        $log = Get-LatestLog
        foreach ($weapon in $script:ArmyWeapons) {
            if ($log -match " - $([regex]::Escape($weapon)): 1(\r?\n|$)") {
                Write-Ok "[session2] $weapon is back in the storehouse, once"
            } else {
                Add-Failure "[session2] $weapon did not come back to the storehouse exactly once"
            }
        }
        $after = Get-KingdomMoney $server $KingdomName
        if ($after -eq $before) {
            Write-Ok "[session2] the army made no money: $after"
        } else {
            Add-Failure "[session2] the army changed the money: $before -> $after"
        }
    }
    # The companion, its pack and the party survived the restart unchanged (the owner was away all the time), and the money
    # adds up.
    if ($script:CompanionEntry) {
        if (Wait-ForLog $server 'KingdomWorld companions: 1 companion\(s\), \d+ market\(s\), 1 part\(ies\), data format 1' 10) {
            Write-Ok '[session2] the companion and the party were loaded after the restart'
        } else {
            Add-Failure '[session2] the companion and the party were not loaded after the restart'
        }
        $seen = Count-Log ', level (\d+), loyalty (\d+), food (\d+), health (\d+), .*, items (\d+)'
        Send-ServerCommand $server "kingdomworld admin companions list $($script:CompanionOwner)"
        $entry = Wait-ForNewLog $server ', level (\d+), loyalty (\d+), food (\d+), health (\d+), .*, items (\d+)' $CommandTimeout $seen
        $now = if ($entry) { "$($entry.Groups[1].Value)/$($entry.Groups[2].Value)/$($entry.Groups[3].Value)/$($entry.Groups[4].Value)/$($entry.Groups[5].Value)" } else { '' }
        if ($now -eq $script:CompanionEntry) {
            Write-Ok "[session2] the companion and its pack are exactly as they were: $now"
        } else {
            Add-Failure "[session2] the companion changed in the restart: '$now', expected '$($script:CompanionEntry)'"
        }
        if ($script:CompanionWeapon) {
            $seen = Count-Log 'Gear mainhand: (\S+)'
            Send-ServerCommand $server "kingdomworld admin companions gear $($script:CompanionOwner)"
            $hand = Wait-ForNewLog $server 'Gear mainhand: (\S+)' $CommandTimeout $seen
            if ($hand -and $hand.Groups[1].Value -eq $script:CompanionWeapon) {
                Write-Ok "[session2] the companion still takes $($script:CompanionWeapon)"
            } else {
                Add-Failure "[session2] the weapon of the mod was lost in the restart"
            }
        }
        Send-ServerCommand $server 'kingdomworld admin companions status'
        if (Wait-ForLog $server 'Companion data format 1: 1 slave\(s\), 0 free companion\(s\), 1 part\(ies\)' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] one slave and one party in the data'
        } else {
            Add-Failure '[session2] the companion data does not hold one slave and one party'
        }
    }

    if (-not $script:ConstructionId) { return }
    # The law survived the restart: the wanted, the confiscated goods, the debt, the reputation and the pass are the same, and
    # the money adds up. Then the murderer buys the goods back at the throne for half their worth and gets all 18 items.
    if ($script:LawConfiscation) {
        if (Wait-ForLog $server 'KingdomWorld law: 1 wanted, 1 confiscation\(s\), \d+ court\(s\), data format 1' 10) {
            Write-Ok '[session2] the law was loaded after the restart'
        } else {
            Add-Failure '[session2] the law was not loaded with one wanted and one confiscation'
        }
        $seen = Count-Log '#\d+ of \S+ in \S+: (\d+) item\(s\) worth (\d+), ransom (\d+)'
        Send-ServerCommand $server 'kingdomworld admin law confiscated'
        $goods = Wait-ForNewLog $server "#$($script:LawConfiscation) of \S+ in \S+: (\d+) item\(s\) worth (\d+), ransom (\d+)" $CommandTimeout $seen
        if ($goods -and "$($goods.Groups[1].Value)/$($goods.Groups[2].Value)" -eq $script:LawGoods) {
            Write-Ok "[session2] the confiscated goods are the same: $($script:LawGoods)"
        } else {
            Add-Failure "[session2] the confiscated goods changed: $(if ($goods) { $goods.Value } else { 'missing' }), expected $($script:LawGoods)"
        }
        $seen = Count-Log 'Debt of \S+ to \S+: .* \((\d+)\)'
        Send-ServerCommand $server "kingdomworld admin law debt $($script:LawThief) $KingdomName"
        $debt = Wait-ForNewLog $server 'Debt of \S+ to \S+: .* \((\d+)\)' $CommandTimeout $seen
        if ($debt -and [int64]$debt.Groups[1].Value -eq $script:LawDebt) {
            Write-Ok "[session2] the debt is still $($script:LawDebt)"
        } else {
            Add-Failure "[session2] the debt changed in the restart: $(if ($debt) { $debt.Value } else { 'no answer' })"
        }
        $seen = Count-Log 'Reputation of \S+ in \S+: (-?\d+)'
        Send-ServerCommand $server "kingdomworld admin law reputation $($script:LawThief) $KingdomName"
        $reputation = Wait-ForNewLog $server 'Reputation of \S+ in \S+: (-?\d+)' $CommandTimeout $seen
        if ($reputation -and $reputation.Groups[1].Value -eq $script:LawReputation) {
            Write-Ok "[session2] the reputation is still $($script:LawReputation)"
        } else {
            Add-Failure '[session2] the reputation changed in the restart'
        }
        Send-ServerCommand $server "kingdomworld admin law pass $($script:LawThief) $KingdomName"
        $pass = Wait-ForLog $server 'Pass of \S+ in \S+: (\d+) tick' $CommandTimeout $ConsoleFailPattern
        if ($pass -and [int64]$pass.Groups[1].Value -gt 0) {
            Write-Ok '[session2] the pass into the cities still holds'
        } else {
            Add-Failure '[session2] the pass was lost in the restart'
        }
        Send-ServerCommand $server "kingdomworld admin law wanted $KingdomName"
        if (Wait-ForLog $server ' in \S+: assault on a citizen; fine ' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the record of the blow is still there'
        } else {
            Add-Failure '[session2] the record of the blow was lost in the restart'
        }
        $before = Get-KingdomMoney $server $KingdomName
        $ransom = if ($goods) { [int64]$goods.Groups[3].Value } else { 0 }
        Send-ServerCommand $server "kingdomworld admin companions fund $($script:LawOutlaw) $KingdomName $ransom"
        [void](Wait-ForLog $server 'Account of .* in the currency: \+' $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin law confiscated ransom $($script:LawConfiscation)"
        $back = Wait-ForLog $server "#$($script:LawConfiscation) bought back by \S+: (\d+) item\(s\) with the owner, ransom (\d+)" $CommandTimeout $ConsoleFailPattern
        $after = Get-KingdomMoney $server $KingdomName
        if ($back -and $back.Groups[1].Value -eq '18' -and [int64]$back.Groups[2].Value -eq $ransom -and $after -eq $before + $ransom) {
            Write-Ok "[session2] the murderer bought back exactly the 18 items for $ransom, half their worth; the money adds up"
        } else {
            Add-Failure "[session2] the ransom went wrong: $(if ($back) { $back.Value } else { 'no answer' }), money $before -> $after"
        }
    }

    # The life of cities survived the restart: the ill citizen is still ill, the tavern is open, and a second festival the same
    # day is refused without touching the money.
    if (Wait-ForLog $server 'KingdomWorld life of cities: (\d+) ill, \d+ room\(s\), (\d+) kingdom\(s\) with festivals, data format 1' 10) {
        Write-Ok '[session2] the life of cities was loaded after the restart'
    } else {
        Add-Failure '[session2] the life of cities was not loaded'
    }
    if ($script:SickId) {
        Send-ServerCommand $server "kingdomworld admin life sick list $KingdomName"
        if (Wait-ForLog $server "Ill in ${KingdomName}: 1" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] citizen $($script:SickId) is still ill"
        } else {
            Add-Failure '[session2] the illness was lost in the restart'
        }
    }
    if ($script:TavernId) {
        Send-ServerCommand $server "kingdomworld admin life tavern $KingdomName"
        if (Wait-ForLog $server "Tavern $($script:TavernId) at -?\d+ -?\d+ -?\d+: open" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the tavern is still open'
        } else {
            Add-Failure '[session2] the tavern is not open after the restart'
        }
    }
    $before = Get-KingdomMoney $server $KingdomName
    Send-ServerCommand $server "kingdomworld admin life festival $KingdomName"
    $refused = Wait-ForLog $server 'Too soon for another festival' $CommandTimeout $ConsoleFailPattern
    $after = Get-KingdomMoney $server $KingdomName
    if ($refused -and $before -ge 0 -and $after -eq $before) {
        Write-Ok '[session2] the festival of the day before the restart is remembered: a second one is refused, the money is the same'
    } else {
        Add-Failure "[session2] the festival was forgotten or the refusal changed the money: $before -> $after"
    }

    # The construction survived the restart exactly; the builder finishes it and nothing of its materials is left over.
    $id = $script:ConstructionId
    $constructions = if ($script:HutId) { 2 } else { 1 }
    if (Wait-ForLog $server "KingdomWorld loaded .* $constructions construction\(s\), \d+ building\(s\)" 10) {
        Write-Ok "[session2] the $constructions construction(s) were loaded after the restart"
    } else {
        Add-Failure '[session2] the constructions were not loaded after the restart'
    }
    if ($script:HutId) {
        Send-ServerCommand $server "kingdomworld admin build blueprints $KingdomName"
        if (Wait-ForLog $server "$([regex]::Escape($script:HutTemplate)) of ${KingdomName}: Smoke hut, role house, 3 x 3 x 3" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the saved blueprint survived the restart'
        } else {
            Add-Failure '[session2] the saved blueprint was lost in the restart'
        }
        $hutPattern = "#$($script:HutId) $([regex]::Escape($script:HutTemplate)) at -7 70 21: steps done (\d+) of (\d+), blocks placed (\d+) of (\d+), paid and waiting (\d+)"
        Send-ServerCommand $server "kingdomworld admin build info $($script:HutId)"
        $hutInfo = Wait-ForLog $server $hutPattern $CommandTimeout $ConsoleFailPattern
        $hutNow = if ($hutInfo) { "$($hutInfo.Groups[1].Value)/$($hutInfo.Groups[2].Value)/$($hutInfo.Groups[3].Value)/$($hutInfo.Groups[4].Value)/$($hutInfo.Groups[5].Value)" } else { '' }
        if ($hutNow -eq $script:HutInfo) {
            Write-Ok "[session2] the construction from the saved blueprint is exactly where it was: $hutNow"
        } else {
            Add-Failure "[session2] the construction from the saved blueprint changed in the restart: '$hutNow', expected '$($script:HutInfo)'"
        }
        Send-ServerCommand $server "kingdomworld admin build resume $($script:HutId)"
        [void](Wait-ForLog $server "Construction #$($script:HutId) goes on" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server 'forceload add -16 16 -1 31'
    }
    $infoPattern = "#$id kingdomworld:small_house at 39 70 15: steps done (\d+) of (\d+), blocks placed (\d+) of (\d+), paid and waiting (\d+)"
    Send-ServerCommand $server "kingdomworld admin build info $id"
    $info = Wait-ForLog $server $infoPattern $CommandTimeout $ConsoleFailPattern
    $now = if ($info) { "$($info.Groups[1].Value)/$($info.Groups[2].Value)/$($info.Groups[3].Value)/$($info.Groups[4].Value)/$($info.Groups[5].Value)" } else { '' }
    if ($now -eq $script:ConstructionInfo) {
        Write-Ok "[session2] the construction is exactly where it was: $now"
    } else {
        Add-Failure "[session2] the construction changed in the restart: '$now', expected '$($script:ConstructionInfo)'"
    }
    Send-ServerCommand $server "kingdomworld admin build resume $id"
    if (-not (Wait-ForLog $server "Construction #$id goes on" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session2] the house could not be let go on after the restart'
    }
    Send-ServerCommand $server 'forceload add 32 0 47 15'
    Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 12"
    [void](Wait-ForLog $server "Ran 12 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server 'kingdomworld admin build tick'
    [void](Wait-ForLog $server 'Constructions moved on: \d+' $CommandTimeout $ConsoleFailPattern)
    Send-ServerCommand $server "kingdomworld admin build buildings $KingdomName"
    if (Wait-ForLog $server "#\d+ ${KingdomName}: kingdomworld:small_house \(house\) at 39 70 15, intact \d+, by an operator false" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session2] the builder finished the house; it is in the register'
    } else {
        Add-Failure '[session2] the house was not finished after the restart'
    }
    if ($script:HutId) {
        if (Wait-ForLog $server "#\d+ ${KingdomName}: $([regex]::Escape($script:HutTemplate)) \(house\) at -7 70 21, intact \d+, by an operator false" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the builder finished the hut of the saved blueprint after the restart; it is in the register as a house'
        } else {
            Add-Failure '[session2] the hut of the saved blueprint was not finished after the restart'
        }
    }
    $before = (Get-LatestLog).Length
    Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
    Start-Sleep -Seconds 3
    $listed = (Get-LatestLog).Substring($before)
    $left = @(@('minecraft:red_bed', 'minecraft:oak_door', 'minecraft:glass_pane', 'minecraft:spruce_stairs', 'minecraft:spruce_slab', 'minecraft:barrel') |
            Where-Object { $listed -match [regex]::Escape($_) })
    if ($left.Count -eq 0) {
        Write-Ok '[session2] the materials add up: nothing the house needed is left over and nothing was missing'
    } else {
        Add-Failure "[session2] materials of the house are left over in the storehouse: $($left -join ', ')"
    }

    # Kingdoms of the world: one appears at the word of an operator away from the other kingdoms, and the server stops in the
    # middle of its appearance; the third session checks that it ends without copies and that its money adds up.
    Send-ServerCommand $server 'kingdomworld admin npc status'
    if (Wait-ForLog $server 'Kingdoms of the world: \d+ places? in the plan, \d+ kingdoms?, generation on, data format 2' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session2] the kingdoms of the world are planned (data format 2)'
    } else {
        Add-Failure '[session2] admin npc status did not answer'
    }
    Send-ServerCommand $server "kingdomworld admin npc spawn $NpcX $NpcZ large"
    $spawning = Wait-ForLog $server 'Place #(\d+): a kingdom of the world \(large\) appears at (-?\d+), (-?\d+) with towns: (\d+)' $CommandTimeout $ConsoleFailPattern
    if ($spawning) {
        $script:NpcSite = $spawning.Groups[1].Value
        Write-Ok "[session2] a large kingdom of the world appears at $($spawning.Groups[2].Value) $($spawning.Groups[3].Value) with $($spawning.Groups[4].Value) town(s)"
        if (Wait-ForLog $server "\d+ chunks claimed for site $($script:NpcSite) " $NpcTimeout) {
            Write-Ok '[session2] the kingdom of the world has its land; the server stops in the middle of its appearance'
        } else {
            Add-Failure '[session2] the kingdom of the world got no land in time'
        }
    } else {
        Add-Failure '[session2] admin npc spawn did not start a kingdom of the world'
    }
}

if ($script:NpcSite) {
    Invoke-Phase 'session3' {
        param($server)
        $site = $script:NpcSite
        if (Wait-ForLog $server "KingdomWorld kingdoms of the world: \d+ place\(s\) planned \(\d+ waiting, \d+ appeared, 1 appearing at \w+\)" 10) {
            Write-Ok '[session3] the appearance goes on after the restart'
        } else {
            Add-Failure '[session3] the appearance was not loaded as going on'
        }
        $appeared = Wait-ForLog $server 'The kingdom of the world (.+?) appeared \(large\): (\d+) buildings, (\d+) citizens, (\d+) soldiers, (\d+) town\(s\); (\d+) ticks, average tick ([\d.]+) ms, worst tick ([\d.]+) ms \(at (\w+)\)' $NpcTimeout
        if (-not $appeared) {
            Add-Failure '[session3] the kingdom of the world did not finish appearing after the restart'
            return
        }
        $name = $appeared.Groups[1].Value
        $script:NpcName = $name
        Write-Ok "[session3] $name appeared: $($appeared.Groups[2].Value) buildings, $($appeared.Groups[3].Value) citizens, $($appeared.Groups[4].Value) soldiers, $($appeared.Groups[5].Value) town(s)"
        $script:NpcTiming = "$($appeared.Groups[6].Value) ticks after the restart, average $($appeared.Groups[7].Value) ms, worst $($appeared.Groups[8].Value) ms (at $($appeared.Groups[9].Value))"
        Write-Ok "[session3] ticks of the appearance after the restart: $($script:NpcTiming)"
        $wood = [regex]::Match((Get-LatestLog), "site $site large at [^\r\n]* got its founding wealth: (\d+) in the treasury, \d+ kinds of goods in the storehouses, the wood of the place (\S+)")
        if ($wood.Success) { Write-Ok "[session3] founding wealth $($wood.Groups[1].Value), the wood of the place $($wood.Groups[2].Value)" }
        if ((Count-Log "appears at -?\d+, -?\d+, -?\d+: large ruled by") -gt 0) {
            Add-Failure '[session3] the kingdom of the world was founded again after the restart'
        }
        Send-ServerCommand $server 'kingdomworld admin npc list'
        $state = Wait-ForLog $server "  #${site}: treasury (\d+), founding (\d+), citizens (\d+), soldiers (\d+), buildings (\d+), towns (\d+)" $CommandTimeout $ConsoleFailPattern
        if (-not $state) {
            Add-Failure '[session3] admin npc list did not show the kingdom of the world'
        } else {
            $treasury = [int64]$state.Groups[1].Value
            $founding = [int64]$state.Groups[2].Value
            if ($founding -eq 10000 -and $treasury -le $founding -and $treasury -gt 0) {
                Write-Ok "[session3] the money adds up: the founding gave 10000 once, the treasury holds $treasury"
            } else {
                Add-Failure "[session3] the money does not add up: founding $founding, treasury $treasury"
            }
            if ([int]$state.Groups[3].Value -eq [int]$appeared.Groups[3].Value) {
                Write-Ok "[session3] no citizen came twice ($($state.Groups[3].Value))"
            } else {
                Add-Failure "[session3] citizens $($state.Groups[3].Value), the appearance made $($appeared.Groups[3].Value)"
            }
        }
        $sites = [regex]::Matches((Get-LatestLog), "#$site large at")
        if ($sites.Count -eq 1) { Write-Ok '[session3] the place is listed once' } else { Add-Failure "[session3] the place is listed $($sites.Count) times" }
        Send-ServerCommand $server 'kingdom list'
        Start-Sleep -Seconds 2
        $copies = [regex]::Matches((Get-LatestLog), [regex]::Escape($name)).Count
        Write-Ok "[session3] $name is named $copies time(s) in the log"
        Send-ServerCommand $server 'kingdomworld admin npc timing'
        [void](Wait-ForLog $server 'The last appearance took \d+ ticks' $CommandTimeout $ConsoleFailPattern)

        # The load: another large kingdom appears from the start without a restart, and every tick of it is measured.
        $seen = Count-Log 'appeared \(large\)'
        Send-ServerCommand $server "kingdomworld admin npc spawn $(-$NpcX) $NpcZ large"
        $second = Wait-ForNewLog $server 'The kingdom of the world (.+?) appeared \(large\): (\d+) buildings, (\d+) citizens, (\d+) soldiers, (\d+) town\(s\); (\d+) ticks, average tick ([\d.]+) ms, worst tick ([\d.]+) ms \(at (\w+)\)' $NpcTimeout $seen
        if (-not $second) {
            Add-Failure '[session3] the second large kingdom of the world did not appear in time'
        } else {
            $script:NpcLoad = "$($second.Groups[6].Value) ticks, average $($second.Groups[7].Value) ms, worst $($second.Groups[8].Value) ms (at $($second.Groups[9].Value))"
            Write-Ok "[session3] $($second.Groups[1].Value) appeared from the start: $($second.Groups[2].Value) buildings, $($second.Groups[3].Value) citizens, $($second.Groups[4].Value) soldiers, $($second.Groups[5].Value) town(s); $($script:NpcLoad)"
            if ([double]$second.Groups[8].Value -gt 50) {
                Add-Failure "[session3] a tick of the appearance of a large kingdom took $($second.Groups[8].Value) ms"
            }
        }
        foreach ($slow in [regex]::Matches((Get-LatestLog), 'A tick of the appearance of [^\r\n]*')) { Write-Host "    $($slow.Value)" }
        # Rulers: ten days of turns at once. The money of the treasury moves only as its journal says, and the kingdom grows:
        # a building is laid out for its builders and goods go to the market or to its neighbours.
        # A kingdom of players offers the ruler swords its soldiers lack at a modest price: the ruler takes the deal in its turn.
        Send-ServerCommand $server "kingdomworld admin storage put minecraft:iron_sword 4 $KingdomName"
        [void](Wait-ForLog $server "Stored 4 of minecraft:iron_sword in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)
        Send-ServerCommand $server "kingdomworld admin trade propose $KingdomName `"$name`" minecraft:iron_sword 4 4"
        if (Wait-ForLog $server "The proposal went to" $CommandTimeout $ConsoleFailPattern) { Write-Ok "[session3] $KingdomName offered $name swords" } else { Add-Failure "[session3] $KingdomName could not offer $name a deal" }
        Send-ServerCommand $server "kingdomworld admin npc days $site 10"
        $days = Wait-ForLog $server 'The ruler of (.+?) took (\d+) turn\(s\): (\d+) decree\(s\) remembered, (\d+) waiting; treasury (-?\d+) -> (-?\d+), journal \+(\d+) -(\d+), difference (-?\d+)' $CommandTimeout $ConsoleFailPattern
        if (-not $days) {
            Add-Failure '[session3] admin npc days did not answer'
        } else {
            if ([int]$days.Groups[2].Value -eq 10) { Write-Ok "[session3] the ruler of $($days.Groups[1].Value) took 10 turns: $($days.Groups[3].Value) decree(s), $($days.Groups[4].Value) waiting" } else { Add-Failure "[session3] the ruler took $($days.Groups[2].Value) turns of 10" }
            if ([int64]$days.Groups[9].Value -eq 0) {
                Write-Ok "[session3] the money adds up with the journal: treasury $($days.Groups[5].Value) -> $($days.Groups[6].Value), +$($days.Groups[7].Value) -$($days.Groups[8].Value)"
            } else {
                Add-Failure "[session3] the treasury and the journal differ by $($days.Groups[9].Value)"
            }
        }
        # The kingdom grows: a new building for its builders, a building raised to its next level or a new town.
        $laid = Wait-ForLog $server 'The ruler of .+? (laid out (\S+) at|founded the town (\S+))|Construction \d+: upgrade of building \d+' 240
        if ($laid) { Write-Ok "[session3] the kingdom grows: $($laid.Groups[0].Value)" } else { Add-Failure '[session3] the ruler neither laid out nor raised a building nor founded a town' }
        Send-ServerCommand $server "kingdomworld admin npc decisions $site"
        $decisions = Wait-ForLog $server ': (\d+) decrees?, (\d+) turn\(s\), waiting: ' $CommandTimeout $ConsoleFailPattern
        Start-Sleep -Seconds 2
        if ($decisions) {
            $script:RulerDecrees = [int]$decisions.Groups[1].Value
            $script:RulerTurns = [int]$decisions.Groups[2].Value
            Write-Ok "[session3] the ruler remembers $($script:RulerDecrees) decree(s) after $($script:RulerTurns) turn(s)"
        } else {
            Add-Failure '[session3] admin npc decisions did not answer'
        }
        $trade = [regex]::Matches((Get-LatestLog), '  day \d+: [^\r\n]*\[(offer|sell|buy|import|accept)\b')
        $kinds = ($trade | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique) -join ', '
        $caravan = [regex]::Match((Get-LatestLog), "A caravan left $([regex]::Escape($KingdomName)) for $([regex]::Escape($name)) with ([^\r\n]+)")
        if ($caravan.Success) { Write-Ok "[session3] the ruler took the deal of $KingdomName, the caravan left with $($caravan.Groups[1].Value); trade decrees: $kinds" } else { Add-Failure "[session3] the ruler did not take the deal of $KingdomName (trade decrees: $kinds)" }
        $chronicle = Count-Log 'Chronicle of .+?: the ruler'
        Write-Ok "[session3] decrees written to the chronicle: $chronicle"
        Send-ServerCommand $server "kingdomworld admin npc needs $site"
        [void](Wait-ForLog $server ' needs: ' $CommandTimeout $ConsoleFailPattern)
    }

    # The memory of rulers outlives a restart; then eight kingdoms of the world take a turn each and their ticks are measured.
    Invoke-Phase 'session4' {
        param($server)
        $site = $script:NpcSite
        if (Wait-ForLog $server 'KingdomWorld kingdoms of the world: .*data format 2' 10) { Write-Ok '[session4] the kingdoms of the world are loaded in data format 2' } else { Add-Failure '[session4] the data format of the kingdoms of the world is not 2' }
        Send-ServerCommand $server "kingdomworld admin npc decisions $site"
        $decisions = Wait-ForLog $server ': (\d+) decrees?, (\d+) turn\(s\), waiting: ' $CommandTimeout $ConsoleFailPattern
        if ($decisions -and [int]$decisions.Groups[2].Value -ge $script:RulerTurns -and [int]$decisions.Groups[1].Value -ge [Math]::Min(1, $script:RulerDecrees)) {
            Write-Ok "[session4] the ruler remembers its $($decisions.Groups[1].Value) decree(s) and $($decisions.Groups[2].Value) turn(s) after the restart"
        } else {
            Add-Failure '[session4] the memory of the ruler was lost in the restart'
        }
        $spots = @(@(1500, 1500), @(-1500, 1500), @(1500, -1500), @(-1500, -1500), @(0, 2200), @(2200, 0))
        foreach ($spot in $spots) {
            $seen = Count-Log 'appeared \(small\)'
            Send-ServerCommand $server "kingdomworld admin npc spawn $($spot[0]) $($spot[1]) small"
            if (-not (Wait-ForNewLog $server 'The kingdom of the world (.+?) appeared \(small\)' $NpcTimeout $seen)) {
                Add-Failure "[session4] a small kingdom of the world did not appear near $($spot[0]) $($spot[1])"
            }
        }
        $kingdoms = Count-Log 'The kingdom of the world .+? appeared \('
        Send-ServerCommand $server 'kingdomworld admin npc turn all'
        $queued = Wait-ForLog $server 'Turns queued: (\d+) kingdoms?' $CommandTimeout $ConsoleFailPattern
        Start-Sleep -Seconds 20
        Send-ServerCommand $server 'kingdomworld admin npc timing'
        $timing = Wait-ForLog $server 'Rulers: (\d+) turn\(s\), (\d+) tick\(s\) with work, average ([\d.]+) ms, worst ([\d.]+) ms, worst without the garbage collector ([\d.]+) ms, worst tick of a turn ([\d.]+) ms' $CommandTimeout $ConsoleFailPattern
        if ($timing -and $queued) {
            $script:RulerLoad = "$($queued.Groups[1].Value) kingdoms, $($timing.Groups[1].Value) turns, $($timing.Groups[2].Value) ticks with work, average $($timing.Groups[3].Value) ms, worst $($timing.Groups[4].Value) ms, worst without the garbage collector $($timing.Groups[5].Value) ms, worst tick of a turn $($timing.Groups[6].Value) ms"
            Write-Ok "[session4] turns of the rulers: $($script:RulerLoad)"
            if ([int]$queued.Groups[1].Value -lt 8) { Add-Failure "[session4] only $($queued.Groups[1].Value) kingdoms of the world took a turn" }
            # A turn (the look at a kingdom, the decisions, the answers) must stay short; laying out a building goes through the construction of
            # the game, whose counting of reservations is shared with players, and is reported as a warning.
            if ([double]$timing.Groups[6].Value -gt 50) { Add-Failure "[session4] a tick of a turn of the rulers took $($timing.Groups[6].Value) ms" }
            if ([double]$timing.Groups[5].Value -gt 50) { Add-Warning "[session4] a tick of the rulers carrying out a decision took $($timing.Groups[5].Value) ms besides the garbage collector" }
        } else {
            Add-Failure '[session4] the turns of the rulers were not measured'
        }
        # Diplomacy and war: two wars of kingdoms of the world far from players and a war of the kingdom of players against the
        # large kingdom of the world, all through the commands of operators; steps of the wars and the ticks of diplomacy are measured.
        $smalls = @([regex]::Matches((Get-LatestLog), 'The kingdom of the world (.+?) appeared \(small\)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
        $declared = 0
        if ($smalls.Count -ge 4) {
            foreach ($pair in @(@($smalls[0], $smalls[1]), @($smalls[2], $smalls[3]))) {
                $seen = Count-Log 'War #\d+ declared: '
                Send-ServerCommand $server "kingdomworld admin war declare `"$($pair[0])`" `"$($pair[1])`" raid now"
                if (Wait-ForNewLog $server 'War #(\d+) declared: (.+?) against (.+?) for raid, active' $CommandTimeout $seen) { $declared++ }
            }
        }
        if ($smalls.Count -ge 5) { $script:SiegeNpc = $smalls[4] }
        if ($declared -eq 2) { Write-Ok '[session4] two wars of kingdoms of the world are going on far from players' } else { Add-Failure "[session4] $declared of 2 wars of kingdoms of the world were declared" }
        Send-ServerCommand $server "kingdomworld admin war declare $KingdomName `"$($script:NpcName)`" indemnity now"
        $war = Wait-ForLog $server "War #(\d+) declared: $([regex]::Escape($KingdomName)) against .+? for indemnity, active" $CommandTimeout $ConsoleFailPattern
        if ($war) {
            $script:WarId = $war.Groups[1].Value
            Write-Ok "[session4] $KingdomName is at war with $($script:NpcName): war #$($script:WarId)"
            Send-ServerCommand $server "kingdomworld admin war score $($script:WarId) attacker 55"
            [void](Wait-ForLog $server "War #$($script:WarId), side attacker: score 55.0" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin war exhaustion $($script:WarId) defender 33"
            [void](Wait-ForLog $server "War #$($script:WarId), side defender: score [\d.]+, exhaustion 33.0" $CommandTimeout $ConsoleFailPattern)
        } else {
            Add-Failure "[session4] $KingdomName could not declare war on $($script:NpcName)"
        }
        # Half a minute of the wars as they go, then thirty steps at once.
        Start-Sleep -Seconds 30
        Send-ServerCommand $server 'kingdomworld admin war tick 30'
        $steps = Wait-ForLog $server 'Steps of the wars: 30, average ([\d.]+) ms, worst ([\d.]+) ms' $CommandTimeout $ConsoleFailPattern
        Send-ServerCommand $server 'kingdomworld admin war timing'
        $ticks = Wait-ForLog $server 'Wars: (\d+) tick\(s\) with work, average ([\d.]+) ms, worst ([\d.]+) ms, worst without the garbage collector ([\d.]+) ms' $CommandTimeout $ConsoleFailPattern
        if ($steps -and $ticks) {
            $script:WarLoad = "8+ kingdoms of the world, 3 wars: $($ticks.Groups[1].Value) ticks of diplomacy, average $($ticks.Groups[2].Value) ms, worst $($ticks.Groups[3].Value) ms, worst without the garbage collector $($ticks.Groups[4].Value) ms; 30 steps of the wars, average $($steps.Groups[1].Value) ms, worst $($steps.Groups[2].Value) ms"
            Write-Ok "[session4] wars: $($script:WarLoad)"
            if ([double]$ticks.Groups[4].Value -gt 50) { Add-Failure "[session4] a tick of diplomacy took $($ticks.Groups[4].Value) ms besides the garbage collector" }
            foreach ($slow in [regex]::Matches((Get-LatestLog), 'A tick of diplomacy took [^\r\n]*')) { Write-Host "    $($slow.Value)" }
            if ([double]$steps.Groups[2].Value -gt 50) { Add-Warning "[session4] a step of the wars took $($steps.Groups[2].Value) ms" }
        } else {
            Add-Failure '[session4] the wars were not measured'
        }
        if ($script:WarId) {
            Send-ServerCommand $server "kingdomworld admin war score $($script:WarId)"
            $score = Wait-ForLog $server "War #$($script:WarId) active: [^\r\n]*?score ([\d.]+) / ([\d.]+), exhaustion ([\d.]+) / ([\d.]+)" $CommandTimeout $ConsoleFailPattern
            if ($score) {
                $script:WarScore = "$($score.Groups[1].Value) / $($score.Groups[2].Value), exhaustion $($score.Groups[3].Value) / $($score.Groups[4].Value)"
                $script:WarNumbers = @([double]$score.Groups[1].Value, [double]$score.Groups[2].Value, [double]$score.Groups[3].Value, [double]$score.Groups[4].Value)
                Write-Ok "[session4] the war of $KingdomName stands at $($script:WarScore)"
            } else {
                Add-Failure '[session4] the score of the war was not shown'
            }
        }
        Send-ServerCommand $server 'kingdomworld admin war list'
        [void](Wait-ForLog $server 'Wars and treaties: \d+' $CommandTimeout $ConsoleFailPattern)
    }

    # Wars outlive a restart with their score and exhaustion; peace with an indemnity moves money between the treasuries and
    # no money appears or vanishes.
    if ($script:WarId) {
        Invoke-Phase 'session5' {
            param($server)
            if (Wait-ForLog $server 'KingdomWorld diplomacy: \d+ treaty\(ies\), (\d+) war\(s\) going on, \d+ open offer\(s\), data format 1' 10) {
                Write-Ok '[session5] diplomacy is loaded in data format 1'
            } else {
                Add-Failure '[session5] diplomacy was not loaded as expected'
            }
            Send-ServerCommand $server "kingdomworld admin war score $($script:WarId)"
            $score = Wait-ForLog $server "War #$($script:WarId) active: [^\r\n]*?score ([\d.]+) / ([\d.]+), exhaustion ([\d.]+) / ([\d.]+)" $CommandTimeout $ConsoleFailPattern
            if ($score) {
                $after = "$($score.Groups[1].Value) / $($score.Groups[2].Value), exhaustion $($score.Groups[3].Value) / $($score.Groups[4].Value)"
                # Exhaustion only grows: a new game day may begin between the two sessions.
                $same = [double]$score.Groups[1].Value -eq $script:WarNumbers[0] -and [double]$score.Groups[2].Value -eq $script:WarNumbers[1] -and [double]$score.Groups[3].Value -ge $script:WarNumbers[2] -and [double]$score.Groups[4].Value -ge $script:WarNumbers[3]
                if ($same) { Write-Ok "[session5] the score and exhaustion outlived the restart: $($script:WarScore) -> $after" } else { Add-Failure "[session5] the war changed in the restart: $($script:WarScore) -> $after" }
            } else {
                Add-Failure '[session5] the war was lost in the restart'
            }
            Send-ServerCommand $server "kingdomworld admin war peace $($script:WarId) $KingdomName 100"
            $peace = Wait-ForLog $server "War #$($script:WarId) ended: [^\r\n]*; the loser paid (\d+), its treasury (-?\d+) -> (-?\d+), the reserves of the winner grew by (\d+), the money of its currency (\d+) -> (\d+)" $CommandTimeout $ConsoleFailPattern
            if ($peace) {
                $paid = [int64]$peace.Groups[1].Value
                $moved = [int64]$peace.Groups[2].Value - [int64]$peace.Groups[3].Value
                if ($paid -gt 0 -and $moved -eq $paid -and [int64]$peace.Groups[4].Value -eq $paid) {
                    Write-Ok "[session5] peace on terms: $($script:NpcName) paid $paid to $KingdomName, treasury $($peace.Groups[2].Value) -> $($peace.Groups[3].Value)"
                } else {
                    Add-Failure "[session5] the indemnity does not add up: paid $paid, the treasury lost $moved, the reserves grew by $($peace.Groups[4].Value)"
                }
            } else {
                Add-Failure '[session5] the war did not end in peace on terms'
            }
            # The money of the currency of the loser, counted in the same tick before and after the peace: trade abroad may add to it
            # between two separate looks, the peace must not.
            if ($peace -and $peace.Groups[5].Value -eq $peace.Groups[6].Value) { Write-Ok "[session5] the money of $($script:NpcName) adds up: $($peace.Groups[5].Value)" } elseif ($peace) { Add-Failure "[session5] the money of $($script:NpcName) changed with the peace: $($peace.Groups[5].Value) -> $($peace.Groups[6].Value)" }
            Send-ServerCommand $server "kingdomworld admin war relation $KingdomName `"$($script:NpcName)`""
            if (Wait-ForLog $server 'bond truce' $CommandTimeout $ConsoleFailPattern) { Write-Ok '[session5] a truce follows the peace' } else { Add-Failure '[session5] no truce after the peace' }
            Send-ServerCommand $server 'kingdomworld admin war status'
            [void](Wait-ForLog $server 'Diplomacy: \d+ treaty\(ies\)' $CommandTimeout $ConsoleFailPattern)
            # War in the world: a kingdom of the world besieges the city of SmokeTest for the goal "city"; the square is held 40%
            # when the server stops, so that the hold must outlive the restart.
            if ($script:SiegeNpc) {
                Send-ServerCommand $server "kingdomworld admin war present $KingdomName on"
                Send-ServerCommand $server "kingdomworld admin war declare `"$($script:SiegeNpc)`" $KingdomName city now"
                $siegeWar = Wait-ForLog $server "War #(\d+) declared: $([regex]::Escape($script:SiegeNpc)) against $([regex]::Escape($KingdomName)) for city, active" $CommandTimeout $ConsoleFailPattern
                if ($siegeWar) {
                    $script:SiegeWarId = $siegeWar.Groups[1].Value
                    Send-ServerCommand $server "kingdomworld admin siege start `"$($script:SiegeNpc)`" $KingdomName `"Smoke Hollow`" 1"
                    Send-ServerCommand $server "kingdomworld admin siege hold `"$($script:SiegeNpc)`" $KingdomName city `"Smoke Hollow`" 40"
                    if (Wait-ForLog $server "Siege hold: war #$($script:SiegeWarId) plaza of Smoke Hollow at 40%" $CommandTimeout $ConsoleFailPattern) {
                        Write-Ok "[session5] $($script:SiegeNpc) besieges Smoke Hollow (war #$($script:SiegeWarId)), the square is held 40%"
                    } else {
                        Add-Failure '[session5] the siege of Smoke Hollow did not start'
                    }
                    foreach ($kingdom in @($KingdomName, $script:SiegeNpc)) {
                        Send-ServerCommand $server "kingdomworld admin siege count `"$kingdom`""
                    }
                    Start-Sleep -Seconds 2
                    $counts = @([regex]::Matches((Get-LatestLog), 'Siege count: (.+?): (\d+) citizens, (\d+) chunks, (\d+) cities, treasury (-?\d+)'))
                    if ($counts.Count -ge 2) {
                        $script:SiegeCitizens = [int]$counts[-2].Groups[2].Value + [int]$counts[-1].Groups[2].Value
                        $script:SiegeCities = [int]$counts[-2].Groups[4].Value + [int]$counts[-1].Groups[4].Value
                        Write-Ok "[session5] before the capture: $($counts[-2].Value); $($counts[-1].Value)"
                    } else {
                        Add-Failure '[session5] the kingdoms of the siege were not counted'
                    }
                } else {
                    Add-Failure "[session5] $($script:SiegeNpc) could not declare war on $KingdomName for a city"
                }
            } else {
                Add-Warning '[session5] no free kingdom of the world for the siege'
            }
        }
    }

    # The hold outlives the restart; then the square is taken, the peace for the city hands it over with its people, and no citizen
    # or city appears or vanishes.
    if ($script:SiegeWarId) {
        Invoke-Phase 'session6' {
            param($server)
            if (Wait-ForLog $server 'KingdomWorld sieges: \d+ gate\(s\), (\d+) siege\(s\), (\d+) hold\(s\), .*data format 1' 10) {
                Write-Ok '[session6] the sieges are loaded in data format 1'
            } else {
                Add-Failure '[session6] the sieges were not loaded as expected'
            }
            Send-ServerCommand $server 'kingdomworld admin siege status'
            if (Wait-ForLog $server "War #$($script:SiegeWarId): plaza \d+, 40%, taken: -" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session6] the square is still held 40% after the restart'
            } else {
                Add-Failure '[session6] the hold of the square was lost in the restart'
            }
            Send-ServerCommand $server "kingdomworld admin war present $KingdomName on"
            Send-ServerCommand $server "kingdomworld admin siege hold `"$($script:SiegeNpc)`" $KingdomName city `"Smoke Hollow`""
            [void](Wait-ForLog $server "Siege hold: war #$($script:SiegeWarId) plaza of Smoke Hollow at 100%" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin war peace $($script:SiegeWarId) `"$($script:SiegeNpc)`""
            if (Wait-ForLog $server "Conquest #\d+: \d+ city\(ies\) went to $([regex]::Escape($script:SiegeNpc))" 30 $ConsoleFailPattern) {
                Write-Ok "[session6] the peace handed Smoke Hollow to $($script:SiegeNpc)"
            } else {
                Add-Failure '[session6] the peace for the city did not hand it over'
            }
            $moved = [regex]::Match((Get-LatestLog), 'Conquest #\d+: (\d+) citizen\(s\) became citizens of')
            foreach ($kingdom in @($KingdomName, $script:SiegeNpc)) {
                Send-ServerCommand $server "kingdomworld admin siege count `"$kingdom`""
            }
            Start-Sleep -Seconds 2
            $counts = @([regex]::Matches((Get-LatestLog), 'Siege count: (.+?): (\d+) citizens, (\d+) chunks, (\d+) cities, treasury (-?\d+)'))
            if ($counts.Count -ge 2) {
                $citizens = [int]$counts[-2].Groups[2].Value + [int]$counts[-1].Groups[2].Value
                $cities = [int]$counts[-2].Groups[4].Value + [int]$counts[-1].Groups[4].Value
                Write-Ok "[session6] after the capture: $($counts[-2].Value); $($counts[-1].Value); citizens moved: $(if ($moved.Success) { $moved.Groups[1].Value } else { '?' })"
                if ($citizens -eq $script:SiegeCitizens -and $cities -eq $script:SiegeCities) {
                    Write-Ok "[session6] citizens and cities add up: $citizens citizens, $cities cities"
                } else {
                    Add-Failure "[session6] citizens or cities do not add up: $($script:SiegeCitizens) -> $citizens citizens, $($script:SiegeCities) -> $cities cities"
                }
            } else {
                Add-Failure '[session6] the kingdoms were not counted after the capture'
            }
            Send-ServerCommand $server 'kingdomworld admin siege timing'
            [void](Wait-ForLog $server 'The longest second of the sieges: \d+ us' $CommandTimeout $ConsoleFailPattern)
        }
    }

    # The nobility: the courts of the kingdoms of the world appear with their houses; SmokeTest raises a house with a title, a
    # plot of it brews, a party demands, a spy is hired and sent; all of it and the money outlive a restart.
    Invoke-Phase 'session7' {
        param($server)
        if (Wait-ForLog $server 'KingdomWorld nobility: \d+ house\(s\), \d+ title\(s\), \d+ court\(s\), \d+ demand\(s\), \d+ plot\(s\), \d+ spy\(ies\), data format 1' 10) {
            Write-Ok '[session7] the nobility is loaded in data format 1'
        } else {
            Add-Failure '[session7] the nobility was not loaded as expected'
        }
        Send-ServerCommand $server 'kingdomworld admin nobility tick'
        $status = Wait-ForLog $server 'Nobility: (\d+) house\(s\), (\d+) title\(s\), (\d+) court\(s\)' $CommandTimeout $ConsoleFailPattern
        if ($status -and [int]$status.Groups[1].Value -gt 0) {
            Write-Ok "[session7] the courts of the world appeared: $($status.Groups[1].Value) houses, $($status.Groups[2].Value) titles, $($status.Groups[3].Value) courts"
        } else {
            Add-Failure '[session7] no houses appeared in the kingdoms of the world'
        }
        $seen = Count-Log 'House #\d+ added'
        Send-ServerCommand $server "kingdomworld admin nobility house $KingdomName add"
        $added = Wait-ForNewLog $server 'House #(\d+) added' $CommandTimeout $seen
        if ($added) {
            $script:NobleHouse = [regex]::Matches((Get-LatestLog), 'House #(\d+) added')[-1].Groups[1].Value
            Send-ServerCommand $server "kingdomworld admin nobility title house $KingdomName $($script:NobleHouse) count m1"
            [void](Wait-ForLog $server 'Done\.' $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin nobility plot $($script:NobleHouse) 2"
            [void](Wait-ForLog $server "Plot #\d+: 2 of \d+" $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin nobility demand $($script:NobleHouse) build 100"
            [void](Wait-ForLog $server 'Demand #\d+: build 100' $CommandTimeout $ConsoleFailPattern)
            Send-ServerCommand $server "kingdomworld admin nobility spy hire $KingdomName"
            Start-Sleep -Seconds 1
            Send-ServerCommand $server "kingdomworld admin nobility spy list $KingdomName"
            $spy = Wait-ForLog $server '#(\d+) [^\r\n]*, skill (\d+), idle, -' $CommandTimeout $ConsoleFailPattern
            if ($spy -and $script:NpcName) {
                $script:NobleSpy = $spy.Groups[1].Value
                Send-ServerCommand $server "kingdomworld admin nobility spy send $KingdomName $($script:NobleSpy) scout `"$($script:NpcName)`""
                [void](Wait-ForLog $server 'Done\.' $CommandTimeout $ConsoleFailPattern)
                Write-Ok "[session7] house #$($script:NobleHouse) is a count with a plot and a demand; spy #$($script:NobleSpy) scouts $($script:NpcName)"
            } else {
                Add-Failure '[session7] no spy could be hired and sent'
            }
        } else {
            Add-Failure "[session7] no house could be added to $KingdomName"
        }
        Send-ServerCommand $server "kingdomworld admin nobility stability $KingdomName"
        $stability = Wait-ForLog $server "${KingdomName}: stability (\d+),([^;]*);" $CommandTimeout $ConsoleFailPattern
        if ($stability) { Write-Ok "[session7] stability of ${KingdomName}: $($stability.Groups[1].Value) ($($stability.Groups[2].Value.Trim()))" } else { Add-Failure '[session7] the stability was not shown' }
        # Every kingdom of the world keeps a spy; then the day of the courts, one kingdom a tick, measured.
        Send-ServerCommand $server 'kingdomworld admin nobility spy hireall'
        $hired = Wait-ForLog $server 'Kingdoms of the world hired (\d+) spy\(ies\)' $CommandTimeout $ConsoleFailPattern
        if ($hired) { Write-Ok "[session7] kingdoms of the world hired $($hired.Groups[1].Value) spies" }
        Send-ServerCommand $server 'kingdomworld admin nobility day'
        Start-Sleep -Seconds 8
        Send-ServerCommand $server 'kingdomworld admin nobility timing'
        $timing = Wait-ForLog $server 'Ticks of the nobility: (\d+), average ([\d.]+) ms, worst ([\d.]+) ms' $CommandTimeout $ConsoleFailPattern
        if ($timing) {
            $script:NobilityTiming = "$($timing.Groups[1].Value) ticks, average $($timing.Groups[2].Value) ms, worst $($timing.Groups[3].Value) ms"
            if ([double]$timing.Groups[3].Value -le 50) { Write-Ok "[session7] the day of the courts: $($script:NobilityTiming)" } else { Add-Failure "[session7] a tick of the nobility is too slow: $($script:NobilityTiming)" }
        } else {
            Add-Failure '[session7] the timing of the nobility was not shown'
        }
        Send-ServerCommand $server 'kingdomworld admin nobility status'
        $before = Wait-ForLog $server 'Nobility: (\d+) house\(s\), (\d+) title\(s\), (\d+) court\(s\), (\d+) demand\(s\), (\d+) plot\(s\), (\d+) spy\(ies\)' $CommandTimeout $ConsoleFailPattern
        if ($before) { $script:NobilityCounts = "$($before.Groups[1].Value) $($before.Groups[2].Value) $($before.Groups[4].Value) $($before.Groups[5].Value) $($before.Groups[6].Value)" }
        $script:NobleMoney = Get-KingdomMoney $server $KingdomName
    }

    Invoke-Phase 'session8' {
        param($server)
        $loaded = Wait-ForLog $server 'KingdomWorld nobility: (\d+) house\(s\), (\d+) title\(s\), (\d+) court\(s\), (\d+) demand\(s\), (\d+) plot\(s\), (\d+) spy\(ies\), data format 1' 10
        if ($loaded) {
            $counts = "$($loaded.Groups[1].Value) $($loaded.Groups[2].Value) $($loaded.Groups[4].Value) $($loaded.Groups[5].Value) $($loaded.Groups[6].Value)"
            if ($counts -eq $script:NobilityCounts) { Write-Ok "[session8] houses, titles, demands, plots and spies outlived the restart: $counts" } else { Add-Failure "[session8] the nobility changed in the restart: $($script:NobilityCounts) -> $counts" }
        } else {
            Add-Failure '[session8] the nobility was not loaded'
        }
        $money = Get-KingdomMoney $server $KingdomName
        if ($money -ge 0 -and $money -eq $script:NobleMoney) { Write-Ok "[session8] the money of $KingdomName adds up: $money" } else { Add-Failure "[session8] the money of $KingdomName changed in the restart: $($script:NobleMoney) -> $money" }
        if ($script:NobleHouse) {
            Send-ServerCommand $server "kingdomworld admin nobility house $KingdomName"
            if (Wait-ForLog $server "#$($script:NobleHouse) [^\r\n]*loyalty \d+, wealth \d+, count#\d+@m1" $CommandTimeout $ConsoleFailPattern) { Write-Ok "[session8] the count of $KingdomName kept the title" } else { Add-Failure '[session8] the title of the house was lost' }
        }
        if ($script:NobleSpy) {
            Send-ServerCommand $server "kingdomworld admin nobility spy list $KingdomName"
            if (Wait-ForLog $server "#$($script:NobleSpy) [^\r\n]*, (mission|idle), " $CommandTimeout $ConsoleFailPattern) { Write-Ok '[session8] the spy outlived the restart' } else { Add-Failure '[session8] the spy was lost in the restart' }
        }
        Send-ServerCommand $server "kingdomworld admin nobility stability $KingdomName"
        [void](Wait-ForLog $server "${KingdomName}: stability \d+" $CommandTimeout $ConsoleFailPattern)
    }
}

Write-Summary
if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
