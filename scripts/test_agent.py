"""Integration test for the AI Advisor Agent.

Prerequisites:
    docker compose up -d          # start mysql + backend
    # or: cd backend && mvnw spring-boot:run  # if not using Docker

Usage:
    python scripts/test_agent.py
"""
import json, urllib.request, urllib.error, os, sys

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:18080")

def api(method, path, body=None):
    """Call the backend API. Returns (status, body_json)."""
    url = f"{BACKEND}{path}"
    data = json.dumps(body).encode("utf-8") if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if data:
        req.add_header("Content-Length", str(len(data)))
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8")
        print(f"  DEBUG HTTP {e.code}: {raw_body}", file=sys.stderr)
        try:
            return e.code, json.loads(raw_body)
        except Exception:
            return e.code, {"error": raw_body}

def sse_stream(path, body, token):
    """Stream SSE events from the backend. Yields each event."""
    url = f"{BACKEND}{path}"
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "text/event-stream")
    req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            for line_bytes in resp:
                line = line_bytes.decode("utf-8").rstrip("\r\n")
                if line.startswith("data:"):
                    data_str = line[5:].strip()
                    if not data_str:
                        continue
                    # Try structured event (must be a dict with "type" key)
                    try:
                        parsed = json.loads(data_str)
                    except json.JSONDecodeError:
                        parsed = None
                    if isinstance(parsed, dict) and "type" in parsed:
                        yield parsed  # structured event
                    else:
                        yield data_str  # plain text token (could be number, string, or untyped JSON)
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8")
        print(f"  DEBUG SSE HTTP {e.code}: {raw_body}", file=sys.stderr)
    except Exception as e:
        print(f"Stream error: {e}")

# -- Step 1: Register a test user --
print("=" * 60)
print("Step 1: Register test user")
print("=" * 60)
import random
suffix = random.randint(1000, 9999)
status, auth = api("POST", "/api/auth/register", {
    "username": f"agent_test_{suffix}",
    "email": f"agent_test_{suffix}@test.com",
    "password": "test123456",
    "displayName": "Agent Test"
})
if status not in (200, 201):
    print(f"Register failed: {auth}")
    sys.exit(1)
token = auth["accessToken"]
print(f"Registered as user #{auth['userId']} — token: {token[:20]}...")

# Create an auth helper
def auth_api(method, path, body=None):
    """Call API with auth token."""
    url = f"{BACKEND}{path}"
    data = json.dumps(body).encode("utf-8") if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {token}")
    if data:
        req.add_header("Content-Length", str(len(data)))
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8")
        print(f"  DEBUG HTTP {e.code}: {raw_body}", file=sys.stderr)
        try:
            return e.code, json.loads(raw_body)
        except Exception:
            return e.code, {"error": raw_body}
print("\n" + "=" * 60)
print("Step 2: Complete onboarding")
print("=" * 60)
status, profile = auth_api("PUT", "/api/onboarding/profile", {
    "dateOfBirth": "1995-06-15",
    "heightCm": 175.0,
    "currentWeightKg": 72.0,
    "sex": "MALE",
    "dailyRoutine": "MOSTLY_SITTING",
    "activityLevel": "MODERATE",
    "exercisePreferences": ["RUNNING", "SWIMMING"],
    "coreNeeds": ["WEIGHT_MANAGEMENT", "IMPROVE_SLEEP"]
})
if status not in (200, 201):
    print(f"Onboarding failed: {profile}")
    sys.exit(1)
print(f"Profile saved: {profile.get('heightCm')}cm, {profile.get('currentWeightKg')}kg")

# -- Step 3: Add some tracker data --
print("\n" + "=" * 60)
print("Step 3: Seed tracker data")
print("=" * 60)
from datetime import datetime, timedelta
base = datetime.utcnow()
tracker_data = [
    ("WEIGHT", 72.0, base - timedelta(days=6)),
    ("WEIGHT", 71.5, base - timedelta(days=4)),
    ("WEIGHT", 71.2, base - timedelta(days=2)),
    ("SLEEP", 420, base - timedelta(days=6)),
    ("SLEEP", 380, base - timedelta(days=5)),
    ("SLEEP", 450, base - timedelta(days=4)),
    ("SLEEP", 360, base - timedelta(days=3)),
    ("SLEEP", 410, base - timedelta(days=2)),
    ("SLEEP", 540, base - timedelta(days=1)),  # anomaly — 9h
    ("STEPS", 8500, base - timedelta(days=6)),
    ("STEPS", 9200, base - timedelta(days=5)),
    ("STEPS", 7800, base - timedelta(days=4)),
    ("STEPS", 10500, base - timedelta(days=3)),
    ("STEPS", 8900, base - timedelta(days=2)),
    ("STEPS", 7200, base - timedelta(days=1)),
]
for type_name, amount, date in tracker_data:
    status, result = auth_api("POST", "/api/tracker-entries", {
        "type": type_name,
        "amount": amount,
        "recordedAt": date.strftime("%Y-%m-%dT12:00:00Z")
    })
    if status not in (200, 201):
        print(f"  FAIL: {type_name} {amount} → {result}")
    else:
        print(f"  #{result['id']}: {type_name} {amount} {result['unit']}")

# -- Step 4: Create AI Advisor session --
print("\n" + "=" * 60)
print("Step 4: Create AI Advisor session")
print("=" * 60)
status, session = auth_api("POST", "/api/ai-advisor/sessions")
session_id = session["id"]
print(f"Session created: #{session_id}")

# -- Step 5: Test queries --
print("\n" + "=" * 60)
print("Step 5: Test Agent queries")
print("=" * 60)

test_queries = [
    "Record my weight as 70.8 kg today.",
    "What's my average sleep time over the last 7 days?",
    "Add a note to my weight entries: 'feeling lighter'",  # needs query first to find ID
]

for query in test_queries:
    print(f"\n{'-' * 40}")
    print(f"USER: {query}")
    print(f"{'-' * 40}")
    had_tool = False
    final_reply = ""
    for event in sse_stream(f"/api/ai-advisor/sessions/{session_id}/messages/stream",
                            {"content": query}, token):
        if isinstance(event, dict):
            ev_type = event.get("type")
            if ev_type == "tool_call":
                print(f"  [TOOL] CALL: {event['name']}({event.get('args', '')})")
                had_tool = True
            elif ev_type == "tool_result":
                status_icon = "[OK]" if event.get("success") else "[ERROR]"
                print(f"  {status_icon} RESULT: {event['name']}")
            elif ev_type == "done":
                print(f"  [SAVED] Message saved as #{event.get('messageId')}")
            elif ev_type == "error":
                print(f"  [ERROR] {event.get('message')}")
            else:
                print(f"  [event: {ev_type}]")
        else:
            try:
                print(event, end="", flush=True)
            except UnicodeEncodeError:
                print(event.encode("ascii", errors="replace").decode("ascii"), end="", flush=True)
            final_reply += event
    if had_tool:
        print(f"\n  [INFO] Agent used tools to answer")
    if final_reply:
        print(f"\n  ->Full reply length: {len(final_reply)} chars")

print("\n" + "=" * 60)
print("Done! Check docker compose logs -f for server-side logs.")
print(f"Test user: agent_test_{suffix} / test123456")
print("=" * 60)
