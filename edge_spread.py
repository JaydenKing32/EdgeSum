#!/usr/bin/env python3
import copy
import csv
import os
import re
from argparse import ArgumentParser
from datetime import datetime
from typing import Dict, List


class Video:
    def __init__(self, name: str, dash_down_time: float = 0, down_time: float = 0,
                 sum_time: float = 0, return_time: float = 0):
        self.name = name
        self.dash_down_time = dash_down_time
        self.down_time = down_time
        self.sum_time = sum_time
        self.return_time = return_time


class Summarisation:
    def __init__(self, log_dir: str, master: str, devices: Dict[str, Dict[str, Video]], videos: Dict[str, Video],
                 schedule: str = None, seg_num: int = None, algorithm: str = None):
        self.log_dir = log_dir
        self.master = master
        self.master_path = "{}.log".format(os.path.join(self.log_dir, self.master))
        self.devices = devices
        self.videos = videos
        self.schedule = schedule
        self.seg_num = seg_num
        self.nodes = len([log for log in os.listdir(self.log_dir) if log.endswith(".log")])
        self.algorithm = algorithm
        self.time = get_total_time(self.master_path)

    def get_master_short_name(self) -> str:
        return self.master[-4:]

    def get_time_string(self) -> str:
        return "{} ({:.11})".format(
            self.time.total_seconds(), str(self.time))

    def get_sub_log_dir(self) -> str:
        i = self.log_dir.index(os.path.sep) + 1
        return self.log_dir[i:]


parser = ArgumentParser(description="Generates spreadsheets from logs")
parser.add_argument("dir", help="directory of logs")
parser.add_argument("-o", "--output", default="results.csv", help="name of output file")
args = parser.parse_args()

serial_numbers = {
    "34d8": "00a6a4630f4e34d8",  # Nexus 5X
    "2802": "ce12171c8a14c72802",  # Samsung Galaxy S8
    "1825": "0b3b6fd50c371825",  # Nexus 5
    "9c8f": "00b7a59265959c8f"  # Nexus 5X
}

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

    with open(os.path.join(log_dir, master_filename), 'r') as master_log:
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
    master_sn = serial_numbers[log_dir.split('master-')[1].split(os.sep)[0]]
    worker_logs = [log for log in os.listdir(log_dir) if log.endswith(".log") and master_sn not in log]

    for log in worker_logs:
        with open(os.path.join(log_dir, log), 'r') as work_log:
            device_name = log[-8:-4]

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


def make_offline_spreadsheet(log_dir: str, runs: List[Summarisation], out_name: str):
    with open(out_name, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)
        writer.writerow(["Offline"])

        for (path, dirs, files) in sorted([(path, dirs, files) for (path, dirs, files) in os.walk(log_dir)
                                           if "offline" in path and "verbose" in dirs]):
            for log in files:
                log_path = os.path.join(path, log)

                with open(log_path, 'r') as offline_log:
                    device_sn = log[:-4]
                    videos = {}  # type: Dict[str, Video]
                    device = {device_sn[-4:]: videos}

                    run = Summarisation(path, device_sn, device, videos)
                    runs.append(run)

                    writer.writerow(["Device: {}".format(run.get_master_short_name())])
                    writer.writerow(["Filename", "Download time (s)", "Summarisation time (s)"])

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
                writer.writerow(["Actual total time", run.get_time_string()])

        writer.writerow('')


def make_spreadsheet(run: Summarisation, out: str):
    with open(run.master_path, 'r') as master_log:
        for line in master_log:
            pref = re_pref.match(line)

            if pref is not None:
                master_log.readline()  # skip auto download line
                algo = master_log.readline().split()[-1]
                fast = master_log.readline().split()[-1] == "true"
                seg = master_log.readline().split()[-1] == "true"
                master_log.readline()  # skip auto segmentation line
                seg_num = int(master_log.readline().split()[-1]) if seg else 1

                run.algorithm = algo
                run.schedule = "fast" if fast else "non-fast"
                run.seg_num = seg_num

    devices = copy.deepcopy(run.devices)
    if algo != "best_or_local":
        # Remove master device unless best or local is used
        devices.pop(run.get_master_short_name())

    with open(out, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)

        writer.writerow([
            "Master: {}".format(run.get_master_short_name()),
            "Scheduling mode: {}".format("fast" if fast else "non-fast"),
            "Segments: {}".format(seg_num),
            "Nodes: {}".format(run.nodes),
            "Algorithm: {}".format(algo),
            "Dir: {}".format(run.get_sub_log_dir())
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
        writer.writerow(["Actual total time", run.get_time_string()])
        writer.writerow('')


def edge_spread(root: str, out: str):
    root = os.path.normpath(root)
    runs = []  # type: List[Summarisation]

    make_offline_spreadsheet(root, runs, out)

    for (path, dirs, files) in sorted([(path, dirs, files) for (path, dirs, files) in os.walk(root)
                                       if "master" in path and "verbose" in dirs]):
        master_sn = serial_numbers[path.split('master-')[1].split(os.sep)[0]]
        logs = [log for log in os.listdir(path) if log.endswith(".log")]
        devices = {device[-8:-4]: {} for device in logs}  # Initialise device dictionary with empty dictionaries

        videos = parse_master_log(devices, "{}.log".format(master_sn), path)
        parse_worker_logs(devices, videos, path)

        run = Summarisation(path, master_sn, devices, videos)
        make_spreadsheet(run, out)
        runs.append(run)

    with open(out, 'a', newline='') as csv_f:
        writer = csv.writer(csv_f)

        writer.writerow(["Summary"])
        writer.writerow(["Dir", "Total time (s)", "Human-readable time"])

        for run in runs:
            writer.writerow([run.get_sub_log_dir(), run.time.total_seconds(), "{:.11}\t".format(str(run.time))])


edge_spread(args.dir, args.output)
