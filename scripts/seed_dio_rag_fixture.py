#!/usr/bin/env python3
"""Seed a realistic 90-day RAG fixture for the AI Advisor.

Creates or reuses:
  username: Dio
  password: 12345678

Prerequisites:
  docker compose up -d

Usage:
  python scripts/seed_dio_rag_fixture.py
  python scripts/seed_dio_rag_fixture.py --backend http://localhost:18080
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import date, datetime, time as dtime, timedelta, timezone


USERNAME = "Dio"
PASSWORD = "12345678"
EMAIL = "dio@example.test"
DISPLAY_NAME = "Dio"
START_DATE = date(2026, 4, 9)
END_DATE = date(2026, 7, 7)


def iso(day: date, hour: int, minute: int = 0) -> str:
    return datetime.combine(day, dtime(hour, minute), timezone.utc).isoformat().replace("+00:00", "Z")


class Api:
    def __init__(self, backend: str, token: str | None = None):
        self.backend = backend.rstrip("/")
        self.token = token

    def call(self, method: str, path: str, body=None, timeout=30):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(f"{self.backend}{path}", data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if self.token:
            req.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8")
                return resp.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8")
            try:
                parsed = json.loads(raw)
            except Exception:
                parsed = {"message": raw}
            return exc.code, parsed


def ensure_user(api: Api):
    status, auth = api.call("POST", "/api/auth/register", {
        "username": USERNAME,
        "email": EMAIL,
        "password": PASSWORD,
        "displayName": DISPLAY_NAME,
    })
    if status in (200, 201):
        return auth
    status, auth = api.call("POST", "/api/auth/login", {
        "identifier": USERNAME,
        "password": PASSWORD,
    })
    if status not in (200, 201):
        raise RuntimeError(f"Could not register or login Dio: HTTP {status} {auth}")
    return auth


def complete_onboarding(api: Api):
    status, body = api.call("PUT", "/api/onboarding/profile", {
        "dateOfBirth": "1998-04-12",
        "heightCm": 178.0,
        "currentWeightKg": 75.6,
        "sex": "MALE",
        "ethnicity": None,
        "targetWeightKg": 78.0,
        "goalDurationWeeks": 20,
        "dailyRoutine": "MIXED",
        "activityLevel": "HIGH",
        "exercisePreferences": ["STRENGTH", "RUNNING", "CYCLING", "GYM_WORKOUT"],
        "coreNeeds": ["TRACK_EXERCISE", "BUILD_FITNESS", "HEALTHY_MEAL_PLANNING", "IMPROVE_SLEEP"],
    })
    if status not in (200, 201):
        raise RuntimeError(f"Onboarding failed: HTTP {status} {body}")


def cleanup_with_api(api: Api):
    for status, sessions in [api.call("GET", "/api/ai-advisor/sessions")]:
        if status == 200:
            for session in sessions:
                api.call("DELETE", f"/api/ai-advisor/sessions/{session['id']}")

    from_time = iso(START_DATE, 0)
    to_time = iso(END_DATE + timedelta(days=1), 0)

    status, foods = api.call("GET", f"/api/food/entries?from={from_time}&to={to_time}")
    if status == 200:
        for entry in foods:
            api.call("DELETE", f"/api/food/entries/{entry['id']}")

    for tracker_type in ["WEIGHT", "WORKOUT", "STEPS", "SLEEP", "WATER"]:
        status, page = api.call(
            "GET",
            f"/api/tracker-entries?type={tracker_type}&from={from_time}&to={to_time}&page=0&size=500",
        )
        if status == 200:
            for entry in page.get("content", []):
                api.call("DELETE", f"/api/tracker-entries/{entry['id']}")


def cleanup_rag_with_docker(user_id: int):
    sql = (
        f"DELETE FROM rag_documents WHERE user_id={user_id}; "
        f"DELETE FROM chat_messages WHERE session_id IN (SELECT id FROM chat_sessions WHERE user_id={user_id}); "
        f"DELETE FROM chat_sessions WHERE user_id={user_id};"
    )
    cmd = [
        "docker", "compose", "exec", "-T", "mysql", "sh", "-c",
        'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" "$@"',
        "mysql", "-e", sql,
    ]
    try:
        subprocess.run(cmd, cwd=os.path.dirname(os.path.dirname(__file__)), check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print("Cleared Dio RAG/chat rows with docker compose mysql.")
    except Exception:
        print("Could not clear rag_documents through Docker; continuing with API cleanup only.")


@dataclass
class DayPlan:
    day: date
    phase: str
    weight: float
    sleep_min: int
    steps: int
    water_ml: int
    calories: int
    protein: int
    workout: tuple[str, int, str] | None


def build_day_plans():
    random.seed(20260708)
    days = (END_DATE - START_DATE).days + 1
    plans: list[DayPlan] = []
    workout_by_weekday = {
        0: ("Upper strength", 62, "bench press and pull work"),
        1: ("Zone 2 run", 42, "easy aerobic run"),
        2: ("Lower strength", 66, "squat and hinge focus"),
        4: ("Push hypertrophy", 58, "shoulders and chest accessories"),
        5: ("Long cycle", 75, "steady endurance ride"),
    }
    for i in range(days):
        day = START_DATE + timedelta(days=i)
        progress = i / max(1, days - 1)
        if day < date(2026, 5, 9):
            phase = "base building"
            workout_keep = {0, 2, 5}
        elif day < date(2026, 6, 8):
            phase = "volume increase"
            workout_keep = {0, 1, 2, 4}
        else:
            phase = "strength peak"
            workout_keep = {0, 1, 2, 4, 5}

        sleep_hours = 6.35 + 0.85 * progress + random.gauss(0, 0.45)
        steps = int(7600 + 3600 * progress + random.gauss(0, 1200))
        water = int(1700 + 900 * progress + random.gauss(0, 250))
        calories = int(2450 + 520 * progress + random.gauss(0, 140))
        protein = int(125 + 45 * progress + random.gauss(0, 8))

        notes = ""
        if date(2026, 5, 13) <= day <= date(2026, 5, 19):
            sleep_hours = random.uniform(4.2, 5.2)
            steps = random.randint(3800, 6100)
            water = random.randint(1000, 1450)
            notes = "stressful work week, poor sleep"
        if date(2026, 6, 10) <= day <= date(2026, 6, 16):
            sleep_hours = random.uniform(7.6, 8.7)
            steps = random.randint(5200, 7600)
            calories -= 180
            notes = "planned deload week"

        workout = None
        if day.weekday() in workout_keep:
            name, duration, detail = workout_by_weekday[day.weekday()]
            duration = int(duration + 14 * progress + random.gauss(0, 5))
            workout_note = detail
            if day in {date(2026, 5, 27), date(2026, 6, 24), date(2026, 7, 1)}:
                workout_note += "; personal record effort"
            if date(2026, 6, 10) <= day <= date(2026, 6, 16):
                workout_note = "deload intensity; technique focus"
                duration = max(30, duration - 20)
            workout = (name, duration, workout_note)

        weight = 72.1 + 3.55 * progress + 0.25 * math.sin(i / 6) + random.gauss(0, 0.18)
        plans.append(DayPlan(
            day=day,
            phase=phase,
            weight=round(weight, 1),
            sleep_min=int(max(210, min(570, sleep_hours * 60))),
            steps=max(1500, steps),
            water_ml=max(700, water),
            calories=max(1800, calories),
            protein=max(80, protein),
            workout=workout,
        ))
        if notes:
            plans[-1].phase = f"{phase}; {notes}"
    return plans


def create_tracker(api: Api, tracker_type: str, day: date, amount, detail=None, notes=None, hour=12):
    status, body = api.call("POST", "/api/tracker-entries", {
        "type": tracker_type,
        "recordedAt": iso(day, hour),
        "amount": amount,
        "detail": detail,
        "notes": notes,
    })
    if status not in (200, 201):
        raise RuntimeError(f"Failed {tracker_type} {day}: HTTP {status} {body}")
    return body


def create_food(api: Api, plan: DayPlan):
    meals = [
        ("BREAKFAST", "Oats, banana, whey protein", 520, 38, 72, 11, 9, 430, 8),
        ("LUNCH", "Chicken rice bowl with vegetables", 760, 55, 88, 18, 8, 520, 12),
        ("DINNER", "Salmon, potatoes, salad", 820, 52, 78, 30, 10, 560, 19),
    ]
    if plan.calories > 2700:
        meals.append(("SNACK", "Greek yogurt and trail mix", 360, 27, 36, 13, 4, 260, 16))
    scale = plan.calories / sum(m[2] for m in meals)
    protein_scale = plan.protein / max(1, sum(m[3] for m in meals))

    for meal_type, name, cal, pro, carbs, fat, fiber, grams, hour in meals:
        status, body = api.call("POST", "/api/food/entries/analyzed", {
            "recordedAt": iso(plan.day, hour),
            "mealType": meal_type,
            "notes": plan.phase if meal_type == "DINNER" else None,
            "items": [{
                "name": name,
                "grams": round(grams * scale, 1),
                "calories": round(cal * scale, 1),
                "proteinGrams": round(pro * protein_scale, 1),
                "carbohydrateGrams": round(carbs * scale, 1),
                "fatGrams": round(fat * scale, 1),
                "fiberGrams": round(fiber * scale, 1),
            }],
        })
        if status not in (200, 201):
            raise RuntimeError(f"Failed food {meal_type} {plan.day}: HTTP {status} {body}")


def seed(api: Api):
    plans = build_day_plans()
    for index, plan in enumerate(plans, start=1):
        notes = plan.phase
        create_tracker(api, "SLEEP", plan.day, plan.sleep_min, notes=notes, hour=7)
        create_tracker(api, "STEPS", plan.day, plan.steps, notes=notes, hour=22)
        create_tracker(api, "WATER", plan.day, plan.water_ml, notes=notes, hour=22)
        if index % 3 == 1 or plan.day == END_DATE:
            create_tracker(api, "WEIGHT", plan.day, plan.weight, notes="morning weigh-in", hour=8)
        if plan.workout:
            name, duration, workout_notes = plan.workout
            create_tracker(api, "WORKOUT", plan.day, duration, detail=name, notes=workout_notes, hour=18)
        create_food(api, plan)
        if index % 10 == 0:
            print(f"Seeded {index}/{len(plans)} days...")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", default=os.environ.get("BACKEND_URL", "http://localhost:18080"))
    parser.add_argument("--no-reset", action="store_true", help="Do not delete Dio's existing fixture data first.")
    parser.add_argument("--skip-rag-db-clean", action="store_true", help="Do not try docker-based rag_documents cleanup.")
    args = parser.parse_args()

    public_api = Api(args.backend)
    auth = ensure_user(public_api)
    token = auth["accessToken"]
    user_id = auth["userId"]
    api = Api(args.backend, token)
    complete_onboarding(api)
    print(f"Ready: Dio user_id={user_id}, password={PASSWORD}")

    if not args.no_reset:
        cleanup_with_api(api)
        if not args.skip_rag_db_clean:
            cleanup_rag_with_docker(user_id)

    started = time.time()
    seed(api)
    elapsed = time.time() - started
    print(f"Seeded Dio fixture from {START_DATE} to {END_DATE} in {elapsed:.1f}s.")
    print("Known signals:")
    print("- Weight rises from about 72.1 kg to 75.6 kg.")
    print("- Workouts increase from 3/wk to 5/wk.")
    print("- May 13-19 is a stress week: poor sleep, low steps, low water.")
    print("- Jun 10-16 is a deload week: more sleep, lighter training, lower steps.")
    print("- Calories/protein trend upward for lean mass gain.")


if __name__ == "__main__":
    main()
