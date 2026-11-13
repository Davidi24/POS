#!/usr/bin/env bash
# Runs the POS with one command: the backend (with its database and a mail catcher) in Docker, then the desktop app.
#
#   ./start.sh           backend + desktop app; closing the app stops the backend again (the data stays)
#   ./start.sh backend   only the backend, left running
#   ./start.sh app       only the desktop app, for a backend that's already running
#   ./start.sh stop      stop the backend
#   ./start.sh logs      follow the backend's log
#   ./start.sh reset     delete the database and uploaded photos, back to the demo data (asks first)
#
# Needs Docker (or Podman), and the internet the first time. The desktop app needs Java 17 to 25: yours is used if
# you have one, otherwise a JDK 21 is downloaded once into .tools/ (nothing is installed on the system).
# Linux and macOS; on Windows, run it in WSL.
#
# Settings, e.g. `POS_API_PORT=18080 ./start.sh`:
#   POS_API_PORT  (8080)       the backend's port on this computer
#   POS_MAIL_PORT (8025)       Mailpit: a page with every email the backend sent
#   POS_BIND      (127.0.0.1)  0.0.0.0 lets other devices on your network (a phone) reach the backend

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS="$ROOT/.tools"
export POS_API_PORT="${POS_API_PORT:-8080}"
export POS_MAIL_PORT="${POS_MAIL_PORT:-8025}"
export POS_BIND="${POS_BIND:-127.0.0.1}"
API_URL="http://localhost:$POS_API_PORT"

# Java the desktop build runs on: Gradle 9.1 runs on 17 to 25. Raise JAVA_MAX when Gradle is updated.
JAVA_MIN=17
JAVA_MAX=25
JDK_DOWNLOAD=21

if [ -t 1 ]; then BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; OFF=$'\033[0m'
else BOLD=; GREEN=; YELLOW=; RED=; OFF=; fi
say()  { printf '%s==>%s %s%s%s\n' "$GREEN" "$OFF" "$BOLD" "$*" "$OFF"; }
note() { printf '    %s\n' "$*"; }
warn() { printf '%s!!%s  %s\n' "$YELLOW" "$OFF" "$*" >&2; }
die()  { printf '%sxx%s  %s\n' "$RED" "$OFF" "$*" >&2; exit 1; }

case "$(uname -s)" in
  Linux) OS=linux ;;
  Darwin) OS=mac ;;
  *) die "This script runs on Linux and macOS. On Windows, run it in WSL (Ubuntu) with Docker Desktop." ;;
esac
command -v curl >/dev/null 2>&1 || die "curl is missing. Install it (e.g. sudo apt install curl), then run this again."

# ---------------------------------------------------------------------------------------------------------- Docker

COMPOSE=()
ENGINE=

find_compose() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose); ENGINE=docker
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose); ENGINE=docker
  elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE=(podman compose); ENGINE=podman
  elif command -v podman-compose >/dev/null 2>&1; then
    COMPOSE=(podman-compose); ENGINE=podman
  else
    die "Docker isn't installed. Install Docker Desktop or Docker Engine (https://docs.docker.com/get-docker/), then run this again."
  fi
  local problem
  if command -v "$ENGINE" >/dev/null 2>&1 && ! problem="$("$ENGINE" info 2>&1 >/dev/null)"; then
    printf '%s\n' "$problem" | tail -n 3 >&2
    case "$ENGINE:$problem" in
      docker:*ermission*) die "Docker is running but you aren't allowed to use it. Run: sudo usermod -aG docker \$USER, then log out and in again." ;;
      docker:*) die "Docker is installed but not running. Start Docker Desktop (Linux: sudo systemctl start docker), then run this again." ;;
      podman:*) die "Podman isn't ready. On Linux: systemctl --user start podman.socket; on a Mac: podman machine start." ;;
    esac
  fi
}

compose() { "${COMPOSE[@]}" -f "$ROOT/docker-compose.yml" "$@"; }

running() {  # is this compose service's container running?
  local id
  id="$(compose ps -q "$1" 2>/dev/null | head -n 1)"
  [ -n "$id" ] && [ "$("$ENGINE" inspect -f '{{.State.Running}}' "$id" 2>/dev/null)" = true ]
}

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

host_time_zone() {
  local tz=
  case "${TZ:-}" in ''|:*) ;; *) tz="$TZ" ;; esac
  if [ -z "$tz" ] && command -v timedatectl >/dev/null 2>&1; then tz="$(timedatectl show -p Timezone --value 2>/dev/null || true)"; fi
  if [ -z "$tz" ]; then
    local link; link="$(readlink /etc/localtime 2>/dev/null || true)"
    case "$link" in *zoneinfo/*) tz="${link##*zoneinfo/}" ;; esac
  fi
  if [ -z "$tz" ] && [ -r /etc/timezone ]; then tz="$(head -n 1 /etc/timezone)"; fi
  printf '%s\n' "${tz:-UTC}"
}

BACKEND_WAS_RUNNING=0
STOP_ON_EXIT=0

start_backend() {
  if running backend; then
    BACKEND_WAS_RUNNING=1
  else
    if port_busy "$POS_API_PORT"; then
      die "Port $POS_API_PORT is already used by another program (another backend?). Stop it, or use another port: POS_API_PORT=18080 ./start.sh"
    fi
  fi
  if ! running mail && port_busy "$POS_MAIL_PORT"; then
    die "Port $POS_MAIL_PORT is already used by another program (MailHog?). Stop it, or use another port: POS_MAIL_PORT=18025 ./start.sh"
  fi
  export POS_TZ="${POS_TZ:-$(host_time_zone)}"
  if ! "$ENGINE" image inspect pos-backend:local >/dev/null 2>&1; then
    say "Building the backend in Docker (only slow the first time: a few minutes)"
  else
    say "Starting the backend in Docker"
  fi
  compose up -d --build || die "Docker couldn't start the backend (see above)."
  if [ "$BACKEND_WAS_RUNNING" = 0 ] && [ "${1:-}" = stop-on-exit ]; then STOP_ON_EXIT=1; fi
}

wait_backend() {
  say "Waiting for the backend at $API_URL"
  local tries=0 code
  while :; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$API_URL/" 2>/dev/null || true)"
    if [ -n "$code" ] && [ "$code" != 000 ]; then break; fi
    if ! running backend; then
      compose logs --tail 40 backend >&2 || true
      die "The backend stopped while starting (its last lines are above; ./start.sh logs shows all of it)."
    fi
    tries=$((tries + 1))
    if [ "$tries" -ge 300 ]; then die "The backend didn't answer within 10 minutes. ./start.sh logs shows what it's doing."; fi
    sleep 2
  done
  note "Backend: $API_URL"
  note "Emails:  http://localhost:$POS_MAIL_PORT"
}

stop_on_exit() {
  if [ "$STOP_ON_EXIT" = 1 ]; then
    STOP_ON_EXIT=0
    say "Stopping the backend (the data stays; ./start.sh starts it again)"
    compose stop || true
  fi
}
trap stop_on_exit EXIT
trap 'exit 130' INT TERM

# ------------------------------------------------------------------------------------------------------------ Java

java_major() {  # "21.0.10" -> 21, "1.8.0_402" -> 8
  local v
  v="$("$1" -version 2>&1 | awk -F'"' '/version/ { print $2; exit }')"
  case "$v" in 1.*) v="${v#1.}" ;; esac
  printf '%s\n' "${v%%[.+-]*}"
}

usable_jdk() {  # a JAVA_HOME with a JDK (javac too) of a version the build runs on
  [ -n "$1" ] && [ -x "$1/bin/java" ] && [ -x "$1/bin/javac" ] || return 1
  local major; major="$(java_major "$1/bin/java")"
  case "$major" in ''|*[!0-9]*) return 1 ;; esac
  [ "$major" -ge "$JAVA_MIN" ] && [ "$major" -le "$JAVA_MAX" ]
}

downloaded_jdk() {
  if [ "$OS" = mac ]; then printf '%s\n' "$TOOLS/jdk-$JDK_DOWNLOAD/Contents/Home"; else printf '%s\n' "$TOOLS/jdk-$JDK_DOWNLOAD"; fi
}

find_jdk() {
  local home v candidates=()
  if [ -n "${JAVA_HOME:-}" ]; then candidates+=("$JAVA_HOME"); fi
  if command -v java >/dev/null 2>&1; then
    home="$(java -XshowSettings:properties -version 2>&1 | awk -F' = ' '/^ *java.home = / { print $2; exit }')"
    if [ -n "$home" ]; then candidates+=("$home"); fi
  fi
  if [ "$OS" = mac ] && [ -x /usr/libexec/java_home ]; then
    for v in 21 17 25 24 23 22 20 19 18; do
      if home="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"; then candidates+=("$home"); fi
    done
  fi
  candidates+=("$(downloaded_jdk)")
  for home in "${candidates[@]}"; do
    if usable_jdk "$home"; then printf '%s\n' "$home"; return 0; fi
  done
  return 1
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{ print $1 }'; else shasum -a 256 "$1" | awk '{ print $1 }'; fi
}

# Amazon Corretto 21, the JDK the Gradle build itself runs on (mobile_desktop/gradle/gradle-daemon-jvm.properties), so
# Gradle finds it already there and doesn't download a second one.
download_jdk() {
  local arch os file sum work dir
  case "$(uname -m)" in
    x86_64|amd64) arch=x64 ;;
    aarch64|arm64) arch=aarch64 ;;
    *) die "There's no Java download for this processor ($(uname -m)). Install a JDK $JDK_DOWNLOAD yourself, then run this again." ;;
  esac
  os=linux
  if [ "$OS" = mac ]; then os=macos
  elif ldd --version 2>&1 | grep -qi musl; then os=alpine; fi
  file="amazon-corretto-$JDK_DOWNLOAD-$arch-$os-jdk.tar.gz"
  say "No Java $JAVA_MIN-$JAVA_MAX found: downloading a JDK $JDK_DOWNLOAD (Amazon Corretto) into .tools/, for this project only"
  sum="$(curl -fsSL --retry 3 "https://corretto.aws/downloads/latest_sha256/$file" | awk '{ print $1 }' || true)"
  [ "${#sum}" -eq 64 ] || die "Couldn't reach corretto.aws for the Java download. Check the internet connection and run this again."
  mkdir -p "$TOOLS"
  rm -rf "$TOOLS"/jdk-download.*
  work="$(mktemp -d "$TOOLS/jdk-download.XXXXXX")"
  curl -fL --retry 3 --progress-bar -o "$work/jdk.tar.gz" "https://corretto.aws/downloads/latest/$file" ||
    die "The Java download failed. Run this again to retry."
  [ "$(sha256_of "$work/jdk.tar.gz")" = "$sum" ] || die "The Java download doesn't match its checksum, so it isn't used. Run this again to retry."
  tar -xzf "$work/jdk.tar.gz" -C "$work"
  dir="$(find "$work" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [ -n "$dir" ] || die "The Java download was empty. Run this again to retry."
  rm -rf "$TOOLS/jdk-$JDK_DOWNLOAD"
  mv "$dir" "$TOOLS/jdk-$JDK_DOWNLOAD"
  rm -rf "$work"
}

ensure_java() {
  local home
  if ! home="$(find_jdk)"; then
    download_jdk
    home="$(find_jdk)" || die "The downloaded Java doesn't run on this computer."
  fi
  export JAVA_HOME="$home"
  export PATH="$JAVA_HOME/bin:$PATH"
  note "Java: $JAVA_HOME"
}

# ----------------------------------------------------------------------------------------------------- Desktop app

gradle() {
  (cd "$ROOT/mobile_desktop" && ./gradlew --no-daemon --warning-mode=none -Pkotlin.compiler.execution.strategy=in-process "$@")
}

build_app() {
  say "Building the desktop app (the first time takes several minutes)"
  gradle :desktopApp:jar || die "The desktop app didn't build (see above)."
}

# Linux desktops with a scaled screen (GNOME on Wayland) tell X apps the scale only as Xft.dpi, which Java ignores, so
# the window would come up tiny. Pass the scale on.
screen_scale() {
  [ "$OS" = linux ] || return 0
  case "${JAVA_TOOL_OPTIONS:-}" in *uiScale*) return 0 ;; esac
  [ -z "${GDK_SCALE:-}${J2D_UISCALE:-}" ] || return 0
  command -v xrdb >/dev/null 2>&1 || return 0
  local dpi scale
  dpi="$(xrdb -query 2>/dev/null | awk '/^Xft.dpi:/ { print $2; exit }')"
  [ -n "$dpi" ] || return 0
  scale="$(awk -v d="$dpi" 'BEGIN { s = d / 96; if (s > 1.05) printf "%g", s }')"
  if [ -n "$scale" ]; then export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dsun.java2d.uiScale=$scale"; fi
}

launch_app() {
  say "Opening the desktop app (close its window to finish)"
  note "Demo sign-in: admin / ChangeMe123!"
  export SAPORINI_API_BASE_URL="$API_URL"
  screen_scale
  gradle :desktopApp:run
}

# ------------------------------------------------------------------------------------------------------------ Main

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }

case "${1:-all}" in
  all)
    find_compose
    ensure_java
    start_backend stop-on-exit
    build_app
    wait_backend
    launch_app
    ;;
  backend)
    find_compose
    start_backend
    wait_backend
    say "The backend is running. ./start.sh stop stops it."
    ;;
  app)
    ensure_java
    curl -s -o /dev/null --max-time 3 "$API_URL/" ||
      warn "Nothing answers at $API_URL yet. Start the backend with ./start.sh backend (the app opens anyway)."
    build_app
    launch_app
    ;;
  stop)
    find_compose
    compose stop
    ;;
  logs)
    find_compose
    compose logs -f --tail 200 backend
    ;;
  reset)
    find_compose
    printf 'This deletes the POS database and uploaded photos kept in Docker. Type yes to go on: '
    read -r answer
    [ "$answer" = yes ] || die "Nothing deleted."
    compose down -v
    say "Deleted. ./start.sh starts again with the demo data."
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
