#!/usr/bin/env bash

# https://stackoverflow.com/a/2173421/8031185
trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

usage() {
    printf "Usage: ./logging.sh [-s SERIAL_NUMBER]\n"
    exit 1
}

serials=""

while :; do
    case $1 in
    -s)
        if [[ -z "${2}" ]]; then
            printf "ERROR: No serial number specified.\n"
            usage
        else
            serials="${2}"
            shift 2
        fi
        ;;
    "")
        break
        ;;
    -?*)
        printf "ERROR: Unknown argument: %s\n" "${1}"
        usage
        ;;
    *)
        printf "ERROR: Unknown argument: %s\n" "${1}"
        usage
        ;;
    esac
done

out_dir="./out/$(date +%Y%m%d_%H%M%S)"
verbose_dir="${out_dir}/verbose/"

if [[ ! -d "${verbose_dir}" ]]; then
    mkdir -p "${verbose_dir}"
fi
if [[ -z ${serials} ]]; then
    # Get `adb.exe devices` output, remove \r and "device", skip first line
    serials=$(tail -n +2 <<<"$(adb.exe devices | sed -r 's/(emulator.*)?(device)?\r$//')")
fi

for serial in ${serials}; do
    # Get PID of EdgeSum app in order to filter out logs from other processes
    pid="$(adb.exe -s "${serial}" shell ps | awk '/com\.example\.edgesum/ {print $2}')"
    # Filter out non-EdgeSum logs and only keep log messages prefixed with "!"
    adb.exe -s "${serial}" logcat --pid "${pid}" | grep -P '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+ \w !' >"${out_dir}/${serial}.log" &
    # Save a copy of the verbose output
    adb.exe -s "${serial}" logcat --pid "${pid}" >"${verbose_dir}/${serial}.log" &
done

wait
