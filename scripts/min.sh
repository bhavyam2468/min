#!/usr/bin/env bash
# Talk to the min launcher over adb and print its answer.
#
#   scripts/min.sh help
#   scripts/min.sh add_favorite Google Maps
#   scripts/min.sh set_setting anim_speed 1.5
#
# Everything after the script name is one command line. It is wrapped in single quotes for
# the device shell (adb joins arguments with spaces and re-parses them there), so names with
# spaces arrive whole. Exit status is 0 for "ok"/informational answers, 1 for "error: …".
set -euo pipefail

if [ $# -eq 0 ]; then
  set -- help
fi

cmd="$*"
quoted="'${cmd//\'/\'\\\'\'}'"   # escape any single quote inside the command

out="$(adb shell am broadcast \
  -n com.bhavyam.min/.cli.CliReceiver \
  -a com.bhavyam.min.CLI \
  --es cmd "$quoted")"

# am prints: Broadcast completed: result=<code>, data="<answer, possibly several lines>"
answer="$(printf '%s\n' "$out" | awk '
  { buf = buf $0 "\n" }
  END {
    i = index(buf, "data=\"")
    if (!i) exit 1
    s = substr(buf, i + 6)
    sub(/"[[:space:]]*$/, "", s)
    print s
  }')" || { printf '%s\n' "$out" >&2; exit 1; }

printf '%s\n' "$answer"
case "$answer" in
  error:*) exit 1 ;;
esac
