#!/usr/bin/env python3
"""
Generate 3 months of realistic wellness data for user Leo (user_id=1).
April 1 - June 30, 2026. Outputs SQL to stdout.
"""
import random
import datetime
from datetime import timedelta, timezone

USER_ID = 1
random.seed(42)

# ── Helpers ──
def dt(m, d, h=8, mi=0):
    return f"'{datetime.datetime(2026, m, d, h, mi, 0).isoformat()}+00:00'"

def rnorm(mean, std, lo=None, hi=None):
    v = random.gauss(mean, std)
    if lo is not None: v = max(lo, v)
    if hi is not None: v = min(hi, v)
    return round(v, 2)

def rint(lo, hi):
    return random.randint(lo, hi)

def pick(choices):
    return random.choice(choices)

def esc(s):
    """Escape single quotes for SQL"""
    return s.replace("'", "''")

def nv(v):
    """Null or value"""
    return f"'{esc(v)}'" if v is not None else "NULL"

print("-- Clear old data for Leo")
print("DELETE FROM food_entry_items WHERE food_entry_id IN (SELECT id FROM food_entries WHERE user_id=%d);" % USER_ID)
print("DELETE FROM food_entries WHERE user_id=%d;" % USER_ID)
print("DELETE FROM chat_messages WHERE session_id IN (SELECT id FROM chat_sessions WHERE user_id=%d);" % USER_ID)
print("DELETE FROM chat_sessions WHERE user_id=%d;" % USER_ID)
print("DELETE FROM rag_documents WHERE user_id=%d;" % USER_ID)
print("DELETE FROM tracker_entries WHERE user_id=%d;" % USER_ID)

# ── Tracker entries ──
phases = [
    {"month": 4, "w_mean": 64.2, "sleep_mean": 6.3, "wo_pw": 2, "steps_mean": 7000, "water_mean": 1500, "cal_mean": 2100},
    {"month": 5, "w_mean": 65.5, "sleep_mean": 6.8, "wo_pw": 3, "steps_mean": 9000, "water_mean": 2000, "cal_mean": 2400},
    {"month": 6, "w_mean": 67.0, "sleep_mean": 7.2, "wo_pw": 4, "steps_mean": 10500, "water_mean": 2500, "cal_mean": 2600},
]

tracker_count = 0

print("\n-- Tracker entries")
for phase in phases:
    month = phase["month"]
    days = 30

    for day in range(1, days + 1):
        # SLEEP (daily)
        s = rnorm(phase["sleep_mean"], 1.0, 3.0, 10.0)
        sleep_min = int(s * 60)
        notes_sleep = None
        if s < 5.0:
            notes_sleep = pick(["couldn't fall asleep", "woke up several times", "restless night"])
        t = dt(month, day, 7, 0)
        print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'SLEEP',{t},{sleep_min},NULL,{nv(notes_sleep)},'MANUAL',{t},{t},0);")
        tracker_count += 1

        # STEPS (daily)
        steps = rint(int(phase["steps_mean"]*0.6), int(phase["steps_mean"]*1.5))
        t = dt(month, day, 22, 0)
        print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'STEPS',{t},{steps},NULL,NULL,'MANUAL',{t},{t},0);")
        tracker_count += 1

        # WATER (daily)
        water = rnorm(phase["water_mean"], 400, 300, 4000)
        t = dt(month, day, 22, 0)
        print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'WATER',{t},{int(water)},NULL,NULL,'MANUAL',{t},{t},0);")
        tracker_count += 1

        # WEIGHT (every 3-4 days)
        if day % pick([3, 4]) == 0 or day == 1:
            w = rnorm(phase["w_mean"], 0.4, phase["w_mean"]-1.5, phase["w_mean"]+1.5)
            t = dt(month, day, 8, 30)
            print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'WEIGHT',{t},{w},NULL,NULL,'MANUAL',{t},{t},0);")
            tracker_count += 1

        # WORKOUT (based on per-week rate)
        if random.random() < phase["wo_pw"] / 7:
            dur = rint(30, 90)
            wo_type = pick(["Strength training", "HIIT", "Running", "Cycling", "Swimming", "Yoga"])
            notes_wo = pick([None, None, f"Felt {pick(['strong','great','tired','energized'])} today"])
            t = dt(month, day, rint(7, 19), 0)
            print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'WORKOUT',{t},{dur},{nv(wo_type)},{nv(notes_wo)},'MANUAL',{t},{t},0);")
            tracker_count += 1

        # HEART_RATE (every ~5 days)
        if day % 5 == 0:
            hr = rint(62, 75)
            t = dt(month, day, 8, 0)
            print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'HEART_RATE',{t},{hr},'Resting',NULL,'MANUAL',{t},{t},0);")
            tracker_count += 1

    # Special: bad sleep week in late April
    if month == 4:
        for bad_day in [22, 23, 24, 25, 26]:
            t = dt(4, bad_day, 7, 0)
            print(f"UPDATE tracker_entries SET amount={rint(240,300)}, notes='stressful week, poor sleep' WHERE user_id={USER_ID} AND tracker_type='SLEEP' AND recorded_at={t};")

    # Special: vacation May 15-17
    if month == 5:
        for vday in [15, 16, 17]:
            t = dt(5, vday, 9, 0)
            print(f"UPDATE tracker_entries SET amount={rint(540,600)}, notes='vacation, slept in' WHERE user_id={USER_ID} AND tracker_type='SLEEP' AND recorded_at LIKE '2026-05-{vday:02d}T07%';")
            t = dt(5, vday, 22, 0)
            print(f"UPDATE tracker_entries SET amount={rint(800,1200)}, notes='vacation, forgot to hydrate' WHERE user_id={USER_ID} AND tracker_type='WATER' AND recorded_at LIKE '2026-05-{vday:02d}T22%';")
            print(f"UPDATE tracker_entries SET amount={rint(2000,4000)}, notes='vacation, relaxed day' WHERE user_id={USER_ID} AND tracker_type='STEPS' AND recorded_at LIKE '2026-05-{vday:02d}T22%';")

# ── Food entries ──
print("\n-- Food entries")
food_meals = {
    "BREAKFAST": [
        ("Oatmeal with banana", 350, 45, 8, 10, 5),
        ("Eggs and toast", 400, 28, 30, 15, 2),
        ("Protein smoothie", 380, 35, 30, 12, 4),
        ("Greek yogurt with granola", 320, 40, 18, 8, 3),
    ],
    "LUNCH": [
        ("Chicken breast with rice", 550, 50, 45, 12, 3),
        ("Salmon and quinoa bowl", 520, 42, 38, 18, 5),
        ("Turkey sandwich", 480, 40, 32, 16, 4),
        ("Beef stir-fry with noodles", 600, 55, 35, 22, 3),
    ],
    "DINNER": [
        ("Grilled steak with vegetables", 650, 30, 50, 35, 6),
        ("Pasta with meat sauce", 580, 60, 28, 22, 4),
        ("Fish tacos", 500, 42, 35, 20, 5),
        ("Chicken curry with rice", 620, 55, 38, 24, 4),
    ],
    "SNACK": [
        ("Protein bar", 220, 20, 20, 8, 2),
        ("Apple with peanut butter", 200, 25, 6, 12, 4),
        ("Trail mix", 180, 22, 5, 12, 3),
    ],
}

food_count = 0
for phase in phases:
    month = phase["month"]
    for day in range(1, 31):
        if random.random() < 0.15:  # skip ~15% of days
            continue
        meal_count = rint(2, 4)
        meals_today = random.sample(list(food_meals.keys()), min(meal_count, 4))
        for meal_type in meals_today:
            food_name, cal, carb, pro, fat, fiber = pick(food_meals[meal_type])
            grams = rint(200, 500)
            scale = grams / 350.0
            cal_s = round(cal * scale)
            carb_s = round(carb * scale, 1)
            pro_s = round(pro * scale, 1)
            fat_s = round(fat * scale, 1)
            fiber_s = round(fiber * scale, 1)
            h = pick([8, 12, 18, 22])
            t = dt(month, day, h, 0)

            # FOOD tracker entry
            print(f"INSERT INTO tracker_entries (user_id,tracker_type,recorded_at,amount,detail,notes,source,created_at,updated_at,version) VALUES ({USER_ID},'FOOD',{t},{cal_s},{nv(meal_type)},NULL,'MANUAL',{t},{t},0);")

            # food_entry
            print(f"INSERT INTO food_entries (user_id,tracker_entry_id,recorded_at,source,meal_type,notes,created_at) VALUES ({USER_ID},LAST_INSERT_ID(),{t},'MANUAL',{nv(meal_type)},NULL,{t});")

            # food_entry_item
            print(f"INSERT INTO food_entry_items (food_entry_id,catalog_item_id,food_name,grams,calories,protein_grams,carbohydrate_grams,fat_grams,fiber_grams) VALUES (LAST_INSERT_ID(),NULL,{nv(food_name)},{grams},{cal_s},{pro_s},{carb_s},{fat_s},{fiber_s});")
            food_count += 1

# ── Chat conversations ──
print("\n-- Chat sessions and messages")
import datetime as _dt
chat_sessions = [
    ("Struggling with sleep", [
        ("I've been having trouble sleeping this week. Any advice?",
         "Based on your recent data, I notice your sleep has been around 5-6 hours. Try establishing a consistent bedtime routine, avoid screens 30 minutes before bed, and consider reducing caffeine after 2pm. Your exercise consistency is good — keep that up as it helps sleep quality."),
    ]),
    ("Weight gain progress check", [
        ("How am I doing with my weight gain goal?",
         "Looking at your data, you've gone from 64kg to about 66kg over the past two months — steady progress of about 0.25kg per week. Your calorie intake has increased from ~2100 to ~2400 kcal on average, and your strength training is getting more consistent. Keep doing what you're doing!"),
    ]),
    ("Vacation recovery plan", [
        ("I just got back from vacation and fell off my routine. How do I get back on track?",
         "Don't worry — a few days off is normal. I can see your sleep and hydration dipped during May 15-17, but you've already bounced back. Start with one small win tomorrow: a solid breakfast and a 30-minute workout. The key is not to overcompensate — just return to your normal routine."),
    ]),
    ("Workout plateau", [
        ("I feel like my workouts aren't progressing anymore",
         "I see you've been consistently training 3-4x per week — great consistency! To break through, try varying your routine: increase weights by 5-10%, add drop sets, or try a new exercise. Your nutrition looks solid at ~2500 kcal, but you might benefit from more protein — aim for 150g+ per day."),
    ]),
    ("General wellness check", [
        ("How's my overall health looking?",
         "Overall, great progress! Over the past 3 months: weight up from 64kg to ~67.5kg (solid lean gain), sleep improved from 6.3h to 7.2h average, workout frequency doubled from 2x to 4x per week, and daily steps increased from 7,000 to over 10,000. Your water intake also improved from 1.5L to 2.5L. The only flag was that stressful week in late April — but you recovered well."),
    ]),
]

ts = _dt.datetime(2026, 7, 1, 10, 0, 0)
for title, messages in chat_sessions:
    t = f"'{ts.strftime('%Y-%m-%d %H:%M:%S')}'"
    print(f"INSERT INTO chat_sessions (user_id,title,created_at,updated_at) VALUES ({USER_ID},{nv(title)},{t},{t});")
    print(f"SET @sid = LAST_INSERT_ID();")
    for user_msg, ai_msg in messages:
        ts += _dt.timedelta(seconds=5)
        t = f"'{ts.strftime('%Y-%m-%d %H:%M:%S')}'"
        print(f"INSERT INTO chat_messages (session_id,role,content,created_at) VALUES (@sid,'USER',{nv(user_msg)},{t});")
        ts += _dt.timedelta(seconds=5)
        t = f"'{ts.strftime('%Y-%m-%d %H:%M:%S')}'"
        print(f"INSERT INTO chat_messages (session_id,role,content,created_at) VALUES (@sid,'ASSISTANT',{nv(ai_msg)},{t});")
    ts += _dt.timedelta(minutes=10)

print(f"\n-- Generated ~{tracker_count} tracker entries, ~{food_count} food items, {len(chat_sessions)} chat sessions", file=__import__('sys').stderr)
