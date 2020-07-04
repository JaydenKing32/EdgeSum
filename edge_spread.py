#!/usr/bin/env python3

import csv
import os
import re
from argparse import ArgumentParser
from datetime import datetime
from typing import Dict


class Video:
    def __init__(self, name: str, dash_down_time: float = 0, down_time: float = 0,
                 sum_time: float = 0, return_time: float = 0):
        self.name = name
        self.dash_down_time = dash_down_time
        self.down_time = down_time
        self.sum_time = sum_time
        self.return_time = return_time


parser = ArgumentParser(description="Generates spreadsheets from logs")
parser.add_argument("dir", help="directory of logs")
parser.add_argument("-o", "--output", default="results.csv", help="name of output file")
parser.add_argument("-m", "--master", default="00a6a4630f4e34d8", help="serial number of master device")
parser.add_argument("-f", "--offline", action="store_true", help="specify offline logs")
args = parser.parse_args()

master = args.master
timestamp = r"^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+ "
re_timestamp = re.compile(r"^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})(?=\s+\d+\s+\d+).*(?:\s+)?$")
re_dash_down = re.compile(
    timestamp +
    r"W DashDownloadManager: Successfully downloaded "
    r"(.*)\.mp4 in (\d*\.?\d*)s(?:\s+)?$")
re_down = re.compile(
    timestamp +
    r"W NearbyFragment: Completed downloading "
    r"(.*)\.mp4 from Endpoint{id=\w{4}, name=(.*) \[(\w{4})\]} in (\d*\.?\d*)s(?:\s+)?$")
re_comp = re.compile(timestamp + r"W Summariser: {3}filename: (.*)\.mp4(?:\s+)?$")
re_sum = re.compile(timestamp + r"W Summariser: {3}time: (\d*\.?\d*)s(?:\s+)?$")
re_pref = re.compile(timestamp + r"W NearbyFragment: Preferences:(?:\s+)?$")


def get_video_name(name: str) -> str:
    sep = '!'
    if sep in name:
        return name.split(sep)[0]
    else:
        return name


def timestamp_to_datetime(line: str) -> datetime:
    match = re_timestamp.match(line)

    year = 2020
    month = int(match.group(1))
    day = int(match.group(2))
    hour = int(match.group(3))
    minute = int(match.group(4))
    second = int(match.group(5))
    microsecond = int(int(match.group(6)) * 1000)

    return datetime(year, month, day, hour, minute, second, microsecond)


def get_total_time(master_log_file: str):
    start = None
    end = None

    with open(master_log_file, 'r') as master_log:
        for line in master_log:
            if start is None:
                pref = re_pref.match(line)

                if pref is not None:
                    start = line
            down_match = re_down.match(line)
            comp_match = re_comp.match(line)

            if down_match is not None:
                end = line
            elif comp_match is not None:
                end = line
    return timestamp_to_datetime(end) - timestamp_to_datetime(start)


def parse_master_log(devices: Dict[str, Dict[str, Video]], master_filename: str, log_dir: str) -> Dict[str, Video]:
    videos = {}  # type: Dict[str, Video]

    with open("{}/{}".format(log_dir, master_filename), 'r') as master_log:
        for line in master_log:
            dash_down = re_dash_down.match(line)
            down = re_down.match(line)
            comp = re_comp.match(line)

            if dash_down is not None:
                video_name = get_video_name(dash_down.group(2))
                dash_down_time = float(dash_down.group(3))

                video = Video(name=video_name, dash_down_time=dash_down_time)
                videos[video_name] = video
                devices[master_filename[-8:-4]][video_name] = video
            elif down is not None:
                video_name = get_video_name(down.group(2))
                return_time = float(down.group(5))

                videos[video_name].return_time += return_time
            elif comp is not None:
                video_name = get_video_name(comp.group(2))
                master_log.readline()  # skip active sections line
                time_line = master_log.readline()
                sum_time = float(re_sum.match(time_line).group(2))

                videos[video_name].sum_time += sum_time
    return videos


def parse_worker_logs(devices: Dict[str, Dict[str, Video]], videos: Dict[str, Video], log_dir: str):
    worker_logs = [log for log in os.listdir(log_dir) if log.endswith(".log") and master not in log]

    for filename in worker_logs:
        with open("{}/{}".format(log_dir, filename), 'r') as work_log:
            device_name = filename[-8:-4]

            for line in work_log:
                down = re_down.match(line)
                comp = re_comp.match(line)

                if down is not None:
                    video_name = get_video_name(down.group(2))
                    down_time = float(down.group(5))

                    video = videos[video_name]
                    video.down_time += down_time
                    devices[device_name][video_name] = video
                elif comp is not None:
                    video_name = get_video_name(comp.group(2))
                    work_log.readline()  # skip active sections line
                    time_line = work_log.readline()
                    sum_time = float(re_sum.match(time_line).group(2))

                    videos[video_name].sum_time += sum_time


def make_offline_spreadsheet(log_dir: str, out_name: str):
    offline_logs = [log for log in os.listdir(log_dir) if log.endswith(".log")]

    with open(out_name, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)
        writer.writerow(["Offline"])

        for filename in offline_logs:
            log_path = "{}/{}".format(log_dir, filename)

            with open(log_path, 'r') as offline_log:
                writer.writerow(["Device: {}".format(filename[-8:-4])])
                writer.writerow(["Filename", "Download time (s)", "Summarisation time (s)"])
                videos = {}  # type: Dict[str, Video]

                for line in offline_log:
                    dash_down = re_dash_down.match(line)
                    comp = re_comp.match(line)

                    if dash_down is not None:
                        video_name = get_video_name(dash_down.group(2))
                        dash_down_time = float(dash_down.group(3))

                        videos[video_name] = Video(name=video_name, dash_down_time=dash_down_time)

                    if comp is not None:
                        video_name = get_video_name(comp.group(2))
                        offline_log.readline()  # skip active sections line
                        time_line = offline_log.readline()
                        sum_time = float(re_sum.match(time_line).group(2))

                        videos[video_name].sum_time = sum_time

                for video in videos.values():
                    writer.writerow([video.name, video.dash_down_time, video.sum_time])

            writer.writerow([
                "Total",
                "{:.3f}".format(sum(v.dash_down_time for v in videos.values())),
                "{:.3f}".format(sum(v.sum_time for v in videos.values()))
            ])
            time = get_total_time(log_path)
            writer.writerow(["Actual total time", "{}.{:.3} ({:.11})".format(
                time.seconds, str(time.microseconds), str(time))])

        writer.writerow('')


def make_spreadsheet(devices: Dict[str, Dict[str, Video]], master_filename: str, log_dir: str, out: str):
    master_path = "{}/{}".format(log_dir, master_filename)

    with open(master_path, 'r') as master_log:
        for line in master_log:
            pref = re_pref.match(line)

            if pref is not None:
                master_log.readline()  # skip auto download line
                algo = master_log.readline().split()[-1]
                fast = master_log.readline().split()[-1] == "true"
                seg = master_log.readline().split()[-1] == "true"
                master_log.readline()  # skip auto segmentation line
                seg_num = int(master_log.readline().split()[-1]) if seg else 1

    if algo != "best_or_local":
        # Remove master device unless best or local is used
        devices.pop(master_filename[-8:-4])

    with open(out, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)
        writer.writerow([
            "Master: {}".format(master_filename[-8:-4]),
            "Scheduling mode: {}".format("fast" if fast else "non-fast"),
            "Segments: {}".format(seg_num),
            "Nodes: {}".format(len([log for log in os.listdir(log_dir) if log.endswith(".log")])),
            "Algorithm: {}".format(algo),
            "Dir: {}".format(log_dir)
        ])

        for device_name, video_dict in devices.items():
            writer.writerow(["Device: {}".format(device_name)])

            if not video_dict:
                writer.writerow(["Did not summarise any videos"])
                continue

            writer.writerow([
                "Filename",
                "Download Time (s)",
                "Transfer time (s)",
                "Return time (s)",
                "Summarisation time (s)"
            ])

            for video in video_dict.values():
                writer.writerow([
                    video.name,
                    "{:.3f}".format(video.dash_down_time),
                    "{:.3f}".format(video.down_time) if video.down_time != 0 else "n/a",
                    "{:.3f}".format(video.return_time) if video.return_time != 0 else "n/a",
                    "{:.3f}".format(video.sum_time)
                ])
            total_dash_down_time = sum(v.dash_down_time for v in video_dict.values())
            total_down_time = sum(v.down_time for v in video_dict.values())
            total_return_time = sum(v.return_time for v in video_dict.values())
            total_sum_time = sum(v.sum_time for v in video_dict.values())

            if len(video_dict) > 1:
                writer.writerow([
                    "Total",
                    "{:.3f}".format(total_dash_down_time),
                    "{:.3f}".format(total_down_time) if total_down_time != 0 else "n/a",
                    "{:.3f}".format(total_return_time),
                    "{:.3f}".format(total_sum_time)
                ])
        total_dash_down_time = sum(v.dash_down_time for videos in devices.values() for v in videos.values())
        total_down_time = sum(v.down_time for videos in devices.values() for v in videos.values())
        total_return_time = sum(v.return_time for videos in devices.values() for v in videos.values())
        total_sum_time = sum(v.sum_time for videos in devices.values() for v in videos.values())

        if sum(1 for device in devices.values() if device) > 1:
            writer.writerow([
                "Combined total",
                "{:.3f}".format(total_dash_down_time),
                "{:.3f}".format(total_down_time) if total_down_time != 0 else "n/a",
                "{:.3f}".format(total_return_time),
                "{:.3f}".format(total_sum_time)
            ])
        time = get_total_time(master_path)
        writer.writerow(["Actual total time", "{}.{:.3} ({:.11})".format(
            time.seconds, str(time.microseconds), str(time))])
        writer.writerow('')


def edge_spread(log_dir: str, out: str):
    master_filename = "{}.log".format(master)
    logs = [log for log in os.listdir(log_dir) if log.endswith(".log")]
    devices = {device[-8:-4]: {} for device in logs}  # Initialise device dictionary with empty dictionaries

    videos = parse_master_log(devices, master_filename, log_dir)
    parse_worker_logs(devices, videos, log_dir)
    make_spreadsheet(devices, master_filename, log_dir, out)


if args.offline:
    make_offline_spreadsheet(args.dir, args.output)
else:
    edge_spread(args.dir, args.output)
