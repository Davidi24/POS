# Runs the POS on Windows with one command, like start.sh on Linux and macOS. Start it through start.cmd (from
# PowerShell, cmd or a double click), which runs this file whatever the computer's script policy is.
#
#   .\start.cmd           backend + desktop app; closing the app stops the backend again (the data stays)
#   .\start.cmd backend   only the backend, left running
#   .\start.cmd app       only the desktop app, for a backend that's already running
#   .\start.cmd app 192.168.1.20
#                         only the desktop app, for the backend on another computer on this Wi-Fi (its address; a port
#                         or a whole http:// address works too). Nothing else is needed here: no Docker. To try a till
#                         or tablet, sign in, open Admin Hub > Devices and press "Enter a code".
#   .\start.cmd stop      stop the backend
#   .\start.cmd logs      follow the backend's log
#   .\start.cmd reset     delete the database and uploaded photos, back to the demo data (asks first)
#
# The backend needs Docker Desktop, and the internet the first time. The desktop app needs Java 17 to 25: yours is
# used if you have one, otherwise a JDK 21 is downloaded once into .tools\ (nothing is installed on the system).
#
# Settings, e.g. $env:POS_API_PORT = 18080 before .\start.cmd:
#   POS_API_PORT  (8080)       the backend's port on this computer
#   POS_MAIL_PORT (8025)       Mailpit: a page with every email the backend sent
#   POS_BIND      (127.0.0.1)  0.0.0.0 lets other devices on your network (a phone, another laptop) reach the backend

# Keep this file plain ASCII: Windows PowerShell 5.1 reads a file without a BOM in the old Windows code page.

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$Root = $PSScriptRoot
$Tools = Join-Path $Root '.tools'
$ComposeFile = Join-Path $Root 'docker-compose.yml'
$StopMarker = 'POS_START_STOPPED'

function Get-Setting([string]$Name, [string]$Default) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { $value = $Default }
    Set-Item -Path "env:$Name" -Value $value
    return $value
}
$ApiPort = Get-Setting 'POS_API_PORT' '8080'
$MailPort = Get-Setting 'POS_MAIL_PORT' '8025'
$null = Get-Setting 'POS_BIND' '127.0.0.1'
$ApiUrl = "http://localhost:$ApiPort"

# Java the desktop build runs on: Gradle 9.1 runs on 17 to 25. Raise JavaMax when Gradle is updated (also in start.sh).
$JavaMin = 17
$JavaMax = 25
$JdkDownload = 21
$DownloadedJdk = Join-Path $Tools "jdk-$JdkDownload"

function Say([string]$Text) { Write-Host '==> ' -ForegroundColor Green -NoNewline; Write-Host $Text }
function Note([string]$Text) { Write-Host "    $Text" }
function Warn([string]$Text) { Write-Host '!!  ' -ForegroundColor Yellow -NoNewline; Write-Host $Text }
function Die([string]$Text) {
    Write-Host 'xx  ' -ForegroundColor Red -NoNewline
    Write-Host $Text
    throw $StopMarker
}

# ------------------------------------------------------------------------------------------------- Running programs

# Windows quoting for one command line: quote what has spaces or quotes, escape quotes and the backslashes before them.
function Join-Arguments([string[]]$Arguments) {
    $parts = foreach ($a in $Arguments) {
        if ($a -eq '') { '""' }
        elseif ($a -notmatch '[\s"]') { $a }
        else { '"' + (($a -replace '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"' }
    }
    return ($parts -join ' ')
}

# Runs a program in this window (it shows its own output and progress) and gives back its exit code.
function Invoke-Program([string]$Exe, [string[]]$Arguments, [string]$Directory = $Root) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Exe
    $info.Arguments = Join-Arguments $Arguments
    $info.WorkingDirectory = $Directory
    $info.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($info)
    while (-not $process.WaitForExit(250)) { }
    $process.WaitForExit()
    return $process.ExitCode
}

# Runs a program quietly and gives back its exit code and what it wrote.
function Get-ProgramOutput([string]$Exe, [string[]]$Arguments) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Exe
    $info.Arguments = Join-Arguments $Arguments
    $info.WorkingDirectory = $Root
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    try { $process = [System.Diagnostics.Process]::Start($info) }
    catch { return [pscustomobject]@{ Code = -1; Out = ''; Err = "$($_.Exception.Message)" } }
    $errors = $process.StandardError.ReadToEndAsync()
    $out = $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ Code = $process.ExitCode; Out = $out; Err = $errors.Result }
}

function Find-Program([string]$Name) {
    $found = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { return $found.Path }
    return $null
}

# Does something answer at this address? Any HTTP answer counts, a 401 too.
function Test-Answers([string]$Url, [int]$TimeoutMs) {
    try {
        $request = [System.Net.WebRequest]::Create($Url)
        $request.Timeout = $TimeoutMs
        $request.Proxy = $null
        $response = $request.GetResponse()
        $response.Close()
        return $true
    } catch {
        $e = $_.Exception
        while ($null -ne $e) {
            if ($e -is [System.Net.WebException]) {
                if ($null -ne $e.Response) { $e.Response.Close(); return $true }
                return $false
            }
            $e = $e.InnerException
        }
        return $false
    }
}

function Test-PortBusy([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $attempt = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $attempt.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($attempt)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# ---------------------------------------------------------------------------------------------------------- Docker

$ComposeExe = $null
$ComposeFirst = @()
$Engine = $null

function Find-Compose {
    $docker = Find-Program 'docker.exe'
    $podman = Find-Program 'podman.exe'
    if ($docker -and (Get-ProgramOutput $docker @('compose', 'version')).Code -eq 0) {
        $script:ComposeExe = $docker; $script:ComposeFirst = @('compose'); $script:Engine = $docker
    } elseif (Find-Program 'docker-compose.exe') {
        $script:ComposeExe = Find-Program 'docker-compose.exe'; $script:ComposeFirst = @(); $script:Engine = $docker
    } elseif ($podman -and (Get-ProgramOutput $podman @('compose', 'version')).Code -eq 0) {
        $script:ComposeExe = $podman; $script:ComposeFirst = @('compose'); $script:Engine = $podman
    } else {
        Die "Docker isn't installed. Install Docker Desktop (https://docs.docker.com/desktop/setup/install/windows-install/), then run this again."
    }
    if ($script:Engine) {
        $info = Get-ProgramOutput $script:Engine @('info')
        if ($info.Code -ne 0) {
            ($info.Err -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -Last 3) | ForEach-Object { Write-Host $_ }
            if ($script:Engine -eq $podman) { Die "Podman isn't ready. Run: podman machine start, then run this again." }
            Die "Docker is installed but not running. Start Docker Desktop, wait until it says it's running, then run this again."
        }
    }
}

function Get-ComposeArguments([string[]]$More) {
    return @($script:ComposeFirst) + @('-f', $ComposeFile) + @($More)
}

function Invoke-Compose([string[]]$More) {
    return (Invoke-Program $script:ComposeExe (Get-ComposeArguments $More))
}

function Test-Running([string]$Service) {  # is this compose service's container running?
    $listed = Get-ProgramOutput $script:ComposeExe (Get-ComposeArguments @('ps', '-q', $Service))
    $id = $listed.Out -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1
    if (-not $id -or -not $script:Engine) { return $false }
    $state = Get-ProgramOutput $script:Engine @('inspect', '-f', '{{.State.Running}}', $id.Trim())
    return ($state.Out.Trim() -eq 'true')
}

# The backend runs in this computer's time zone, which Windows names its own way ("W. Europe Standard Time"), so it's
# turned into the tz database's name ("Europe/Berlin"). The table is Unicode CLDR's windowsZones.xml.
function Get-HostTimeZone {
    if ($env:TZ -and -not $env:TZ.StartsWith(':')) { return $env:TZ }
    $id = [TimeZoneInfo]::Local.Id
    if ([TimeZoneInfo].GetMethods() | Where-Object { $_.Name -eq 'TryConvertWindowsIdToIanaId' }) {
        $iana = $null
        if ([TimeZoneInfo]::TryConvertWindowsIdToIanaId($id, [ref]$iana)) { return $iana }
    }
    foreach ($line in ($WindowsZones -split "`n")) {
        $pair = $line.Trim().Split('=')
        if ($pair.Count -eq 2 -and $pair[0] -eq $id) { return $pair[1] }
    }
    return 'UTC'
}

$BackendWasRunning = $false
$StopOnExit = $false

function Start-Backend([switch]$StopOnExitToo) {
    if (Test-Running 'backend') {
        $script:BackendWasRunning = $true
    } elseif (Test-PortBusy ([int]$ApiPort)) {
        Die "Port $ApiPort is already used by another program (another backend?). Stop it, or use another port: `$env:POS_API_PORT = 18080, then .\start.cmd"
    }
    if (-not (Test-Running 'mail') -and (Test-PortBusy ([int]$MailPort))) {
        Die "Port $MailPort is already used by another program (MailHog?). Stop it, or use another port: `$env:POS_MAIL_PORT = 18025, then .\start.cmd"
    }
    $null = Get-Setting 'POS_TZ' (Get-HostTimeZone)
    if ((Get-ProgramOutput $script:Engine @('image', 'inspect', 'pos-backend:local')).Code -ne 0) {
        Say 'Building the backend in Docker (only slow the first time: a few minutes)'
    } else {
        Say 'Starting the backend in Docker'
    }
    if ((Invoke-Compose @('up', '-d', '--build')) -ne 0) { Die "Docker couldn't start the backend (see above)." }
    if (-not $script:BackendWasRunning -and $StopOnExitToo) { $script:StopOnExit = $true }
}

function Wait-Backend {
    Say "Waiting for the backend at $ApiUrl"
    $tries = 0
    while (-not (Test-Answers "$ApiUrl/" 3000)) {
        if (-not (Test-Running 'backend')) {
            $null = Invoke-Compose @('logs', '--tail', '40', 'backend')
            Die 'The backend stopped while starting (its last lines are above; .\start.cmd logs shows all of it).'
        }
        $tries++
        if ($tries -ge 300) { Die "The backend didn't answer within 10 minutes. .\start.cmd logs shows what it's doing." }
        Start-Sleep -Seconds 2
    }
    Note "Backend: $ApiUrl"
    Note "Emails:  http://localhost:$MailPort"
}

function Stop-OnExit {
    if ($script:StopOnExit) {
        $script:StopOnExit = $false
        Say 'Stopping the backend (the data stays; .\start.cmd starts it again)'
        $null = Invoke-Compose @('stop')
    }
}

# ------------------------------------------------------------------------------------------------------------ Java

function Get-JavaMajor([string]$JavaExe) {  # "21.0.10" -> 21, "1.8.0_402" -> 8
    $ran = Get-ProgramOutput $JavaExe @('-version')
    $found = [regex]::Match($ran.Err + $ran.Out, 'version "([^"]+)"')
    if (-not $found.Success) { return 0 }
    $version = $found.Groups[1].Value
    if ($version.StartsWith('1.')) { $version = $version.Substring(2) }
    $major = [regex]::Match($version, '^\d+')
    if ($major.Success) { return [int]$major.Value }
    return 0
}

function Test-Jdk([string]$JdkHome) {  # a JDK (javac too) of a version the build runs on
    if ([string]::IsNullOrWhiteSpace($JdkHome)) { return $false }
    $java = Join-Path $JdkHome 'bin\java.exe'
    $javac = Join-Path $JdkHome 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) { return $false }
    $major = Get-JavaMajor $java
    return ($major -ge $JavaMin -and $major -le $JavaMax)
}

function Find-Jdk {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME.Trim('"').TrimEnd('\')) }
    $java = Find-Program 'java.exe'
    if ($java) {
        $settings = Get-ProgramOutput $java @('-XshowSettings:properties', '-version')
        $found = [regex]::Match($settings.Err + $settings.Out, '(?m)^\s*java\.home = (.+?)\s*$')
        if ($found.Success) { $candidates.Add($found.Groups[1].Value) }
    }
    # Where the usual installers put JDKs, and IntelliJ's downloads.
    $places = @(@('Java', 'Eclipse Adoptium', 'Amazon Corretto', 'Microsoft', 'Zulu', 'BellSoft', 'Semeru') |
        ForEach-Object { Join-Path $env:ProgramFiles $_ })
    $places += (Join-Path $env:USERPROFILE '.jdks')
    foreach ($place in $places) {
        if (Test-Path -LiteralPath $place) {
            Get-ChildItem -LiteralPath $place -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $candidates.Add($_.FullName) }
        }
    }
    $candidates.Add($DownloadedJdk)
    foreach ($candidate in $candidates) {
        if (Test-Jdk $candidate) { return $candidate }
    }
    return $null
}

# Amazon Corretto 21, the JDK the Gradle build itself runs on (mobile_desktop/gradle/gradle-daemon-jvm.properties), so
# Gradle finds it already there and doesn't download a second one.
function Get-Jdk {
    $arch = $env:PROCESSOR_ARCHITEW6432
    if (-not $arch) { $arch = $env:PROCESSOR_ARCHITECTURE }
    if ($arch -ne 'AMD64') {
        Die "There's no Java download here for this processor ($arch). Install a JDK $JdkDownload (for example: winget install Microsoft.OpenJDK.$JdkDownload), then run this again."
    }
    $file = "amazon-corretto-$JdkDownload-x64-windows-jdk.zip"
    Say "No Java $JavaMin-$JavaMax found: downloading a JDK $JdkDownload (Amazon Corretto) into .tools\, for this project only"
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $web = New-Object System.Net.WebClient
    $sum = ''
    try { $sum = ($web.DownloadString("https://corretto.aws/downloads/latest_sha256/$file").Trim() -split '\s+')[0] } catch { }
    if ($sum.Length -ne 64) { Die "Couldn't reach corretto.aws for the Java download. Check the internet connection and run this again." }
    if (-not (Test-Path -LiteralPath $Tools)) { $null = New-Item -ItemType Directory -Path $Tools }
    Get-ChildItem -LiteralPath $Tools -Directory -Filter 'jdk-download.*' -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force }
    $work = Join-Path $Tools ('jdk-download.' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Path $work
    $zip = Join-Path $work 'jdk.zip'
    $url = "https://corretto.aws/downloads/latest/$file"
    $curl = Join-Path $env:SystemRoot 'System32\curl.exe'
    if (Test-Path -LiteralPath $curl) {
        $code = Invoke-Program $curl @('-fL', '--retry', '3', '--progress-bar', '-o', $zip, $url)
        if ($code -ne 0) { Die 'The Java download failed. Run this again to retry.' }
    } else {
        Note 'About 200 MB: this takes a few minutes.'
        try { $web.DownloadFile($url, $zip) } catch { Die 'The Java download failed. Run this again to retry.' }
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant() -ne $sum.ToLowerInvariant()) {
        Die "The Java download doesn't match its checksum, so it isn't used. Run this again to retry."
    }
    Note 'Unpacking it'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $work)
    $jdk = Get-ChildItem -LiteralPath $work -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if (-not $jdk) { Die 'The Java download was empty. Run this again to retry.' }
    if (Test-Path -LiteralPath $DownloadedJdk) { Remove-Item -LiteralPath $DownloadedJdk -Recurse -Force }
    Move-Item -LiteralPath $jdk.FullName -Destination $DownloadedJdk
    Remove-Item -LiteralPath $work -Recurse -Force
}

function Use-Java {
    $jdkHome = Find-Jdk
    if (-not $jdkHome) {
        Get-Jdk
        $jdkHome = Find-Jdk
        if (-not $jdkHome) { Die "The downloaded Java doesn't run on this computer." }
    }
    $env:JAVA_HOME = $jdkHome
    $env:PATH = (Join-Path $jdkHome 'bin') + ';' + $env:PATH
    Note "Java: $jdkHome"
}

# ----------------------------------------------------------------------------------------------------- Desktop app

function Invoke-Gradle([string[]]$Tasks) {
    $gradle = @('/d', '/c', 'gradlew.bat', '--no-daemon', '--warning-mode=none',
        '-Pkotlin.compiler.execution.strategy=in-process') + @($Tasks)
    return (Invoke-Program $env:ComSpec $gradle (Join-Path $Root 'mobile_desktop'))
}

function Build-App {
    Say 'Building the desktop app (the first time takes several minutes)'
    if ((Invoke-Gradle @(':desktopApp:jar')) -ne 0) { Die "The desktop app didn't build (see above)." }
}

function Start-App {
    Say 'Opening the desktop app (close its window to finish)'
    Note 'Demo sign-in: admin / ChangeMe123!'
    Note 'A till or tablet: sign in on it, open Admin Hub > Devices and press "Enter a code".'
    $env:SAPORINI_API_BASE_URL = $ApiUrl
    if ((Invoke-Gradle @(':desktopApp:run')) -ne 0) { Die 'The desktop app stopped with an error (see above).' }
}

# ------------------------------------------------------------------------------------------------------------ Main

function Show-Usage {
    foreach ($line in (Get-Content -LiteralPath $PSCommandPath)) {
        if (-not $line.StartsWith('#')) { break }
        Write-Host ($line -replace '^# ?', '')
    }
}

# Opened with a double click: keep the window open after a failure, so its last lines can be read.
function Test-OpenedFromExplorer {
    try {
        $me = Get-CimInstance Win32_Process -Filter "ProcessId=$PID" -ErrorAction Stop
        $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($me.ParentProcessId)" -ErrorAction Stop
        if ($parent.Name -ne 'cmd.exe') { return $false }
        $grandparent = Get-Process -Id $parent.ParentProcessId -ErrorAction Stop
        return ($grandparent.ProcessName -eq 'explorer')
    } catch {
        return $false
    }
}

$WindowsZones = @'
Dateline Standard Time=Etc/GMT+12
UTC-11=Etc/GMT+11
Aleutian Standard Time=America/Adak
Hawaiian Standard Time=Pacific/Honolulu
Marquesas Standard Time=Pacific/Marquesas
Alaskan Standard Time=America/Anchorage
UTC-09=Etc/GMT+9
Pacific Standard Time (Mexico)=America/Tijuana
UTC-08=Etc/GMT+8
Pacific Standard Time=America/Los_Angeles
US Mountain Standard Time=America/Phoenix
Mountain Standard Time (Mexico)=America/Mazatlan
Mountain Standard Time=America/Denver
British Columbia Standard Time=America/Vancouver
Yukon Standard Time=America/Whitehorse
Alberta Standard Time=America/Edmonton
Central America Standard Time=America/Guatemala
Central Standard Time=America/Chicago
Easter Island Standard Time=Pacific/Easter
Central Standard Time (Mexico)=America/Mexico_City
Canada Central Standard Time=America/Regina
Manitoba Standard Time=America/Winnipeg
SA Pacific Standard Time=America/Bogota
Eastern Standard Time (Mexico)=America/Cancun
Eastern Standard Time=America/New_York
Haiti Standard Time=America/Port-au-Prince
Cuba Standard Time=America/Havana
US Eastern Standard Time=America/Indianapolis
Turks And Caicos Standard Time=America/Grand_Turk
Paraguay Standard Time=America/Asuncion
Atlantic Standard Time=America/Halifax
Venezuela Standard Time=America/Caracas
Central Brazilian Standard Time=America/Cuiaba
SA Western Standard Time=America/La_Paz
Pacific SA Standard Time=America/Santiago
Newfoundland Standard Time=America/St_Johns
Tocantins Standard Time=America/Araguaina
E. South America Standard Time=America/Sao_Paulo
SA Eastern Standard Time=America/Cayenne
Argentina Standard Time=America/Buenos_Aires
Greenland Standard Time=America/Godthab
Montevideo Standard Time=America/Montevideo
Magallanes Standard Time=America/Punta_Arenas
Saint Pierre Standard Time=America/Miquelon
Bahia Standard Time=America/Bahia
UTC-02=Etc/GMT+2
Azores Standard Time=Atlantic/Azores
Cape Verde Standard Time=Atlantic/Cape_Verde
UTC=Etc/UTC
GMT Standard Time=Europe/London
Greenwich Standard Time=Atlantic/Reykjavik
Sao Tome Standard Time=Africa/Sao_Tome
Morocco Standard Time=Africa/Casablanca
W. Europe Standard Time=Europe/Berlin
Central Europe Standard Time=Europe/Budapest
Romance Standard Time=Europe/Paris
Central European Standard Time=Europe/Warsaw
W. Central Africa Standard Time=Africa/Lagos
Jordan Standard Time=Asia/Amman
GTB Standard Time=Europe/Bucharest
Middle East Standard Time=Asia/Beirut
Egypt Standard Time=Africa/Cairo
E. Europe Standard Time=Europe/Chisinau
Syria Standard Time=Asia/Damascus
West Bank Standard Time=Asia/Hebron
South Africa Standard Time=Africa/Johannesburg
FLE Standard Time=Europe/Kiev
Israel Standard Time=Asia/Jerusalem
South Sudan Standard Time=Africa/Juba
Kaliningrad Standard Time=Europe/Kaliningrad
Sudan Standard Time=Africa/Khartoum
Libya Standard Time=Africa/Tripoli
Namibia Standard Time=Africa/Windhoek
Arabic Standard Time=Asia/Baghdad
Turkey Standard Time=Europe/Istanbul
Arab Standard Time=Asia/Riyadh
Belarus Standard Time=Europe/Minsk
Russian Standard Time=Europe/Moscow
E. Africa Standard Time=Africa/Nairobi
Iran Standard Time=Asia/Tehran
Arabian Standard Time=Asia/Dubai
Astrakhan Standard Time=Europe/Astrakhan
Azerbaijan Standard Time=Asia/Baku
Russia Time Zone 3=Europe/Samara
Mauritius Standard Time=Indian/Mauritius
Saratov Standard Time=Europe/Saratov
Georgian Standard Time=Asia/Tbilisi
Volgograd Standard Time=Europe/Volgograd
Caucasus Standard Time=Asia/Yerevan
Afghanistan Standard Time=Asia/Kabul
West Asia Standard Time=Asia/Tashkent
Ekaterinburg Standard Time=Asia/Yekaterinburg
Pakistan Standard Time=Asia/Karachi
Qyzylorda Standard Time=Asia/Qyzylorda
India Standard Time=Asia/Calcutta
Sri Lanka Standard Time=Asia/Colombo
Nepal Standard Time=Asia/Katmandu
Central Asia Standard Time=Asia/Bishkek
Bangladesh Standard Time=Asia/Dhaka
Omsk Standard Time=Asia/Omsk
Myanmar Standard Time=Asia/Rangoon
SE Asia Standard Time=Asia/Bangkok
Altai Standard Time=Asia/Barnaul
W. Mongolia Standard Time=Asia/Hovd
North Asia Standard Time=Asia/Krasnoyarsk
N. Central Asia Standard Time=Asia/Novosibirsk
Tomsk Standard Time=Asia/Tomsk
China Standard Time=Asia/Shanghai
North Asia East Standard Time=Asia/Irkutsk
Singapore Standard Time=Asia/Singapore
W. Australia Standard Time=Australia/Perth
Taipei Standard Time=Asia/Taipei
Ulaanbaatar Standard Time=Asia/Ulaanbaatar
Aus Central W. Standard Time=Australia/Eucla
Transbaikal Standard Time=Asia/Chita
Tokyo Standard Time=Asia/Tokyo
North Korea Standard Time=Asia/Pyongyang
Korea Standard Time=Asia/Seoul
Yakutsk Standard Time=Asia/Yakutsk
Cen. Australia Standard Time=Australia/Adelaide
AUS Central Standard Time=Australia/Darwin
E. Australia Standard Time=Australia/Brisbane
AUS Eastern Standard Time=Australia/Sydney
West Pacific Standard Time=Pacific/Port_Moresby
Tasmania Standard Time=Australia/Hobart
Vladivostok Standard Time=Asia/Vladivostok
Lord Howe Standard Time=Australia/Lord_Howe
Bougainville Standard Time=Pacific/Bougainville
Russia Time Zone 10=Asia/Srednekolymsk
Magadan Standard Time=Asia/Magadan
Norfolk Standard Time=Pacific/Norfolk
Sakhalin Standard Time=Asia/Sakhalin
Central Pacific Standard Time=Pacific/Guadalcanal
Russia Time Zone 11=Asia/Kamchatka
New Zealand Standard Time=Pacific/Auckland
UTC+12=Etc/GMT-12
Fiji Standard Time=Pacific/Fiji
Chatham Islands Standard Time=Pacific/Chatham
UTC+13=Etc/GMT-13
Tonga Standard Time=Pacific/Tongatapu
Samoa Standard Time=Pacific/Apia
Line Islands Standard Time=Pacific/Kiritimati
'@

$Command = 'all'
if ($args.Count -ge 1 -and $args[0]) { $Command = ([string]$args[0]).ToLowerInvariant() }
$Address = ''
if ($args.Count -ge 2) { $Address = [string]$args[1] }

$ExitCode = 0
try {
    if ($Command -eq 'all') {
        Find-Compose
        Use-Java
        Start-Backend -StopOnExitToo
        Build-App
        Wait-Backend
        Start-App
    } elseif ($Command -eq 'backend') {
        Find-Compose
        Start-Backend
        Wait-Backend
        Say 'The backend is running. .\start.cmd stop stops it.'
    } elseif ($Command -eq 'app') {
        if ($Address) {
            # The backend on another computer (user, 2026-11-25: try a till on a second laptop).
            if ($Address -match '^https?://') { $ApiUrl = $Address.TrimEnd('/') }
            elseif ($Address -match ':') { $ApiUrl = "http://$Address" }
            else { $ApiUrl = "http://${Address}:$ApiPort" }
            Use-Java
            Say "Using the backend at $ApiUrl"
            if (-not (Test-Answers "$ApiUrl/" 5000)) {
                Warn "Nothing answers at $ApiUrl. Check that its backend runs, that both computers are on the same Wi-Fi, and that its firewall lets port $(([uri]$ApiUrl).Port) in (the app opens anyway)."
            }
        } else {
            Use-Java
            if (-not (Test-Answers "$ApiUrl/" 3000)) {
                Warn "Nothing answers at $ApiUrl yet. Start the backend with .\start.cmd backend (the app opens anyway)."
            }
        }
        Build-App
        Start-App
    } elseif ($Command -eq 'stop') {
        Find-Compose
        $ExitCode = Invoke-Compose @('stop')
    } elseif ($Command -eq 'logs') {
        Find-Compose
        $ExitCode = Invoke-Compose @('logs', '-f', '--tail', '200', 'backend')
    } elseif ($Command -eq 'reset') {
        Find-Compose
        $answer = Read-Host 'This deletes the POS database and uploaded photos kept in Docker. Type yes to go on'
        if ($answer -ne 'yes') { Die 'Nothing deleted.' }
        if ((Invoke-Compose @('down', '-v')) -ne 0) { Die "Docker couldn't delete them (see above)." }
        Say 'Deleted. .\start.cmd starts again with the demo data.'
    } elseif (@('help', '-h', '--help', '/?') -contains $Command) {
        Show-Usage
    } else {
        Show-Usage
        $ExitCode = 1
    }
} catch {
    if ("$($_.Exception.Message)" -ne $StopMarker) {
        Write-Host 'xx  ' -ForegroundColor Red -NoNewline
        Write-Host ("Something went wrong in start.ps1 (line $($_.InvocationInfo.ScriptLineNumber)): " + $_.Exception.Message)
    }
    $ExitCode = 1
} finally {
    Stop-OnExit
}
if ($ExitCode -ne 0 -and (Test-OpenedFromExplorer)) { $null = Read-Host 'Press Enter to close this window' }
exit $ExitCode
