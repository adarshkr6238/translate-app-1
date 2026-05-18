#!/bin/bash
# Sync project to shared storage using cp
DEST="/sdcard/Download/gemini_project/translate-app-1"
mkdir -p "$DEST"
# Use cp with --exclude equivalents (manual or simpler copy)
# Since rsync missing, use cp -r but cautious about big hidden folders
cp -r /data/data/com.termux/files/home/translate-app-1/* "$DEST/"
echo "Project synced to $DEST"
