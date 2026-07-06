"""Quick test: does DeepSeek V4 Pro support OpenAI-compatible tool calling?

Reads LLM_API_KEY, LLM_API_BASE_URL, LLM_MODEL from .env in the project root.
"""
import json, os, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def load_env(path):
    """Parse KEY=VALUE lines from a .env file."""
    env = {}
    if os.path.exists(path):
        with open(path) as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env

env = load_env(os.path.join(ROOT, ".env"))
BASE = env.get("LLM_API_BASE_URL", "https://api.deepseek.com/v1")
KEY = env.get("LLM_API_KEY", "")
MODEL = env.get("LLM_MODEL", "deepseek-v4-pro")

if not KEY:
    print("No LLM_API_KEY found in .env")
    sys.exit(1)

print(f"Testing: {BASE}/chat/completions  model={MODEL}")

payload = json.dumps({
    "model": MODEL,
    "max_tokens": 500,
    "messages": [
        {"role": "system", "content": "You are a wellness data assistant."},
        {"role": "user", "content": "Record my weight as 70.5 kg today."}
    ],
    "tools": [
        {
            "type": "function",
            "function": {
                "name": "create_tracker_entry",
                "description": "Create a new tracker entry for the user",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "tracker_type": {"type": "string", "enum": ["WEIGHT", "SLEEP", "STEPS", "WORKOUT", "WATER", "HEART_RATE", "BLOOD_GLUCOSE", "MEDICINE"]},
                        "amount": {"type": "number", "description": "Numeric value in the tracker's unit"},
                        "recorded_date": {"type": "string", "description": "ISO date string YYYY-MM-DD"},
                        "notes": {"type": "string", "description": "Optional notes"}
                    },
                    "required": ["tracker_type", "amount"]
                }
            }
        }
    ],
    "tool_choice": "auto"
}).encode("utf-8")

req = urllib.request.Request(
    f"{BASE}/chat/completions",
    data=payload,
    headers={
        "Authorization": f"Bearer {KEY}",
        "Content-Type": "application/json"
    }
)

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print(f"Status: {resp.status}")
        choice = data["choices"][0]
        msg = choice["message"]
        print(f"Finish reason: {choice['finish_reason']}")
        print(f"Content: {repr(msg.get('content'))}")
        tc = msg.get("tool_calls")
        if tc:
            print(f"\n[OK] TOOL CALLS DETECTED ({len(tc)}):")
            for t in tc:
                f = t["function"]
                print(f"  -> {f['name']}({f['arguments']})")
        else:
            print("\n[FAIL] No tool_calls in response")
            print("Full message:", json.dumps(msg, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8")
    print(f"HTTP {e.code}: {body}")
except Exception as e:
    print(f"Error: {e}")
