#!/usr/bin/env python3
"""Generate a throwaway chrono-task-ai data set: data.json + settings.json + state.json.

Each task gets N "usages" = N distinct days in taskHistory (some with a daily note), and
lastActivatedAt = start time of its last usage (what TimerService would have recorded).

Usage:
    python3 tools/gen_test_data.py /tmp/chrono-test --tasks 50 --usages 30 --days 180 --seed 42
"""
import argparse
import json
import random
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path

TOPICS = ["API", "Login", "Billing", "Search", "Export", "Dashboard", "Notifications", "Cache",
          "Migration", "Reporting", "Onboarding", "Payments", "Sync", "Backup", "Audit"]
ACTIONS = ["Fix", "Refactor", "Review", "Document", "Test", "Investigate", "Deploy", "Design"]
STATUSES = ["TODO"] * 4 + ["IN_PROGRESS"] * 3 + ["VALIDATION"] * 2 + ["DONE"] * 2 + ["NONE"]
NOTES = ["Call with the team", "Blocked by review", "Pair programming", "Fixed the edge case",
         "Wrote unit tests", "Waiting for feedback", "Deployed on staging"]


def iso_duration(minutes: int) -> str:
    h, m = divmod(minutes, 60)
    return "PT" + (f"{h}H" if h else "") + (f"{m}M" if m or not h else "")


def build_task(i: int, args, rng: random.Random, today: date) -> dict:
    description = f"{rng.choice(ACTIONS)} {rng.choice(TOPICS)} #{i + 1:03d}"  # unique
    task = {
        "id": f"test-{i + 1:04d}",
        "order": i,
        "description": description,
        "status": rng.choice(STATUSES),
        "topPriority": rng.random() < 0.15,
        "markdownContent": f"# {description}\n\n- generated test task\n",
    }
    if rng.random() < 0.3:
        key = f"TEST-{1000 + i}"
        # JSON key is "jira" (Lombok getter isJira() -> Jackson property "jira"), not "isJira"
        task.update(jira=True, jiraUrl=f"https://example.atlassian.net/browse/{key}")

    # A few tasks are never used (bottom of the "Last used" sort)
    if rng.random() < args.never_used_ratio:
        return task

    n_usages = max(1, min(args.days, int(rng.gauss(args.usages, args.usages / 4))))
    days = sorted(rng.sample(range(args.days), n_usages), reverse=True)  # days ago, oldest first
    history = {}
    for days_ago in days:
        day = today - timedelta(days=days_ago)
        # > 2 min, otherwise Task.cleanupHistory() drops past days without a note
        entry = {"duration": iso_duration(rng.randint(5, 240))}
        if rng.random() < 0.2:
            entry["note"] = rng.choice(NOTES)
        history[day.isoformat()] = entry
    task["taskHistory"] = history

    last_day = today - timedelta(days=days[-1])
    start = datetime.combine(last_day, time(rng.randint(8, 17), rng.choice([0, 15, 30, 45])),
                             tzinfo=timezone.utc)
    task["lastActivatedAt"] = start.isoformat().replace("+00:00", "Z")
    return task


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("target", type=Path, help="folder to create (settings.json, state.json, data/data.json)")
    p.add_argument("--tasks", type=int, default=50, help="number of tasks (default 50)")
    p.add_argument("--usages", type=int, default=30, help="average number of used days per task (default 30)")
    p.add_argument("--days", type=int, default=180, help="history depth in days (default 180)")
    p.add_argument("--never-used-ratio", type=float, default=0.1, help="share of never used tasks (default 0.1)")
    p.add_argument("--seed", type=int, default=42, help="random seed, same seed = same file (default 42)")
    args = p.parse_args()

    rng = random.Random(args.seed)
    today = date.today()
    tasks = [build_task(i, args, rng, today) for i in range(args.tasks)]

    data_dir = args.target / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    (data_dir / "data.json").write_text(json.dumps({"tasks": tasks}, indent=2))
    (args.target / "settings.json").write_text(json.dumps({
        "dataStoragePath": str(data_dir.resolve()),
        "jiraRefreshEnabled": False,
        "gitBackupEnabled": False,
    }, indent=2))
    (args.target / "state.json").write_text(json.dumps({"focusTopPriorities": False, "taskSortMode": "LAST_USED"}))

    usages = sum(len(t.get("taskHistory", {})) for t in tasks)
    print(f"{len(tasks)} tasks, {usages} usages -> {data_dir / 'data.json'}")
    print(f"settings: {(args.target / 'settings.json').resolve()}")


if __name__ == "__main__":
    main()
