#!/usr/bin/env bash

# Get adb.exe devices output, remove \r and "device", skip first line
devices=$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(device)?\r$//')")

for serial in ${devices}; do
  pid="$(adb.exe -s "${serial}" shell ps | awk '/com\.example\.edgesum/ {print $2}')"
  adb.exe -s "${serial}" logcat --pid "${pid}" "*:W" >"${serial}.log"
done
