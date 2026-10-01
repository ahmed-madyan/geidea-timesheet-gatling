#!/usr/bin/env bash
# Writes sign-in and, for the task-log simulation, the uploaded varieties file.
# Credentials are never printed.
set -euo pipefail

: "${SIMULATION:?Set SIMULATION to task-log or bulk-approve}"
: "${TIMESHEET_USERNAME:?Username is required}"
: "${TIMESHEET_PASSWORD:?Password is required}"

users="src/test/resources/data/timesheet/users.csv"
varieties="src/test/resources/data/timesheet/task-varieties.csv"

csv_field() {
  local value="$1"
  value=${value//\"/\"\"}
  printf '"%s"' "$value"
}

decrypt_password() {
  local raw="$1"
  if [[ "$raw" != enc:* ]]; then
    printf '%s' "$raw"
    return
  fi
  : "${TIMESHEET_PASSWORD_KEY:?Password key is missing}"
  if ! command -v openssl >/dev/null 2>&1; then
    apt-get update -qq && apt-get install -y -qq openssl >/dev/null
  fi
  local keydir
  keydir="$(mktemp -d)"
  printf '%s' "$TIMESHEET_PASSWORD_KEY" | base64 -d > "$keydir/key.pem"
  printf '%s' "${raw#enc:}" | base64 -d | openssl pkeyutl -decrypt \
    -inkey "$keydir/key.pem" \
    -pkeyopt rsa_padding_mode:oaep \
    -pkeyopt rsa_oaep_md:sha256 \
    -pkeyopt rsa_mgf1_md:sha256
  rm -rf "$keydir"
}

umask 077
password="$(decrypt_password "$TIMESHEET_PASSWORD")"
{
  printf '%s\n' 'username,password'
  printf '%s,%s\n' "$(csv_field "$TIMESHEET_USERNAME")" "$(csv_field "$password")"
} > "$users"
unset password

if [[ "$SIMULATION" == "task-log" ]]; then
  if [[ -n "${TASK_VARIETIES_CSV:-}" ]]; then
    printf '%s\n' "$TASK_VARIETIES_CSV" > "$varieties"
  elif [[ -n "${TASK_VARIETIES_CSV_B64:-}" ]]; then
    printf '%s' "$TASK_VARIETIES_CSV_B64" | base64 -d > "$varieties"
  else
    echo "Task log requires a task-varieties CSV." >&2
    exit 1
  fi
  if ! head -n 1 "$varieties" | grep -q 'ProjectId'; then
    echo "CSV header must include ProjectId, Task Name, Details / Description, Date, and Hours." >&2
    exit 1
  fi
fi

echo "Prepared data for simulation: $SIMULATION"
