#!/data/data/com.termux/files/usr/bin/sh
set -u

OUTPUT="$(PATH=/system/bin:/system/xbin:$PATH /system/bin/am instrument --user 0 -w \
    org.example.speechboard.test/.SpeechBoardLaunchTest 2>&1)"
printf '%s\n' "$OUTPUT"

case "$OUTPUT" in
    *"PASS: SpeechBoard input view launched."*) exit 0 ;;
    *) exit 1 ;;
esac
