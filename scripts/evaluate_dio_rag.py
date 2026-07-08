#!/usr/bin/env python3
"""Evaluate AI Advisor RAG behavior against the Dio fixture.

Prerequisites:
  python scripts/seed_dio_rag_fixture.py
  LLM_API_KEY and EMBEDDING_API_KEY configured on the backend

Usage:
  python scripts/evaluate_dio_rag.py
  python scripts/evaluate_dio_rag.py --no-warmup
"""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path


USERNAME = "Dio"
PASSWORD = "12345678"


QUESTIONS = [
    {
        "id": "long_weight_trend",
        "question": "Over the last three months, what happened to my body weight?",
        "expect": ["weight", "72", "75"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "workout_frequency_trend",
        "question": "How did my workout consistency change from April to July?",
        "expect": ["workout", "3", "5", "week"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "sleep_improvement",
        "question": "Did my sleep improve over the last three months?",
        "expect": ["sleep", "improve"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "stress_week",
        "question": "Was there a specific week where my recovery looked worse than usual?",
        "expect": ["may", "13", "19", "stress", "sleep"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "deload_week",
        "question": "Can you identify my deload week and what changed during it?",
        "expect": ["jun", "10", "16", "deload", "sleep"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "hydration_trend",
        "question": "How has my hydration changed recently compared with earlier in the dataset?",
        "expect": ["water", "hydration", "increase"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "steps_trend",
        "question": "What is the trend in my daily steps?",
        "expect": ["steps", "increase"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "food_bulk",
        "question": "Does my nutrition look like it supports lean mass gain?",
        "expect": ["calorie", "protein", "gain"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "protein",
        "question": "What happened to my protein intake over this period?",
        "expect": ["protein", "increase"],
    },
    {
        "id": "recent_summary",
        "question": "Summarize my most recent week of health data.",
        "expect": ["recent", "week"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "monthly_comparison",
        "question": "Compare April, May, and June at a high level.",
        "expect": ["april", "may", "june"],
    },
    {
        "id": "best_period",
        "question": "Which period looked strongest for training momentum?",
        "expect": ["june", "july", "workout"],
    },
    {
        "id": "worst_period",
        "question": "Which period looked weakest for recovery?",
        "expect": ["may", "stress", "sleep"],
    },
    {
        "id": "actionable_next_step",
        "question": "Based on my patterns, what is one next training adjustment you recommend?",
        "expect": ["progress", "recovery"],
    },
    {
        "id": "avoid_diagnosis",
        "question": "Do these trends mean I have a medical problem?",
        "expect": ["not", "diagnos"],
        "forbid": ["you have a disease", "diagnosed"],
    },
    {
        "id": "remember_deload",
        "question": "Did we have any historical context about a deload or recovery period?",
        "expect": ["deload", "recovery"],
    },
    {
        "id": "sleep_specific",
        "question": "What happened to my sleep around May 13 to May 19?",
        "expect": ["may", "13", "19", "poor", "sleep"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "water_specific",
        "question": "What happened to water intake during the stressful week?",
        "expect": ["water", "low", "stress"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "steps_specific",
        "question": "Were my steps lower during the stressful week?",
        "expect": ["steps", "lower", "stress"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "workout_prs",
        "question": "Do my workout notes show any personal-record efforts?",
        "expect": ["personal record", "workout"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "weight_goal",
        "question": "Am I moving toward my 78 kg target?",
        "expect": ["78", "target", "toward"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "sleep_today_recent",
        "question": "For recent sleep, should I trust long-term summaries or fresh tracker data more?",
        "expect": ["fresh", "tracker", "recent"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "training_balance",
        "question": "Does my training balance strength and cardio?",
        "expect": ["strength", "run", "cycle"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "consistency",
        "question": "What habit was most consistent across the 90 days?",
        "expect": ["sleep", "steps", "water"],
    },
    {
        "id": "risk_overtraining",
        "question": "Do you see any risk of overreaching from my recent training load?",
        "expect": ["recovery", "deload", "sleep"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "memory_vs_fact",
        "question": "When you use old conversation summaries, how should you treat them compared with tracker data?",
        "expect": ["summary", "tracker", "fact"],
    },
    {
        "id": "food_week",
        "question": "How did my food intake support the final training phase?",
        "expect": ["calorie", "protein", "training"],
    },
    {
        "id": "sleep_recovery_recommendation",
        "question": "Give me a recovery recommendation based on my sleep pattern.",
        "expect": ["sleep", "recovery"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "hydration_recommendation",
        "question": "Give me a hydration recommendation based on my data.",
        "expect": ["water", "hydration"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "steps_recommendation",
        "question": "What should I do with steps on heavy lifting days?",
        "expect": ["steps", "lifting"],
    },
    {
        "id": "data_uncertainty",
        "question": "What are the limits of what you can conclude from my data?",
        "expect": ["not", "diagnosis", "uncertain"],
    },
    {
        "id": "ask_current_weight",
        "question": "What is my latest recorded weight?",
        "expect": ["75"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "ask_average_sleep",
        "question": "What is my average sleep over the last 30 days?",
        "expect": ["sleep", "average"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "ask_workout_count",
        "question": "How many workout entries do I have recently?",
        "expect": ["workout", "entries"],
        "require_tool": "query_tracker_data",
    },
    {
        "id": "ask_food_detail",
        "question": "What kind of meals do I usually log?",
        "expect": ["oats", "chicken", "salmon"],
    },
    {
        "id": "ask_periodization",
        "question": "Does my data show any periodization?",
        "expect": ["base", "volume", "deload", "peak"],
    },
]


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

    def sse(self, path: str, body, timeout=240):
        data = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(f"{self.backend}{path}", data=data, method="POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "text/event-stream")
        if self.token:
            req.add_header("Authorization", f"Bearer {self.token}")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            for line_bytes in resp:
                line = line_bytes.decode("utf-8", errors="replace").rstrip("\r\n")
                if not line.startswith("data:"):
                    continue
                payload = line[5:]
                if payload.startswith(" "):
                    payload = payload[1:]
                if not payload:
                    continue
                try:
                    parsed = json.loads(payload)
                except json.JSONDecodeError:
                    parsed = None
                if isinstance(parsed, dict) and "type" in parsed:
                    yield parsed
                else:
                    yield payload


def login(backend: str) -> Api:
    api = Api(backend)
    status, auth = api.call("POST", "/api/auth/login", {
        "identifier": USERNAME,
        "password": PASSWORD,
    })
    if status != 200:
        raise RuntimeError(f"Login failed. Seed Dio first. HTTP {status}: {auth}")
    return Api(backend, auth["accessToken"])


def create_session(api: Api) -> int:
    status, session = api.call("POST", "/api/ai-advisor/sessions")
    if status not in (200, 201):
        raise RuntimeError(f"Could not create AI Advisor session: HTTP {status} {session}")
    return session["id"]


def ask(api: Api, session_id: int, question: str):
    answer = []
    thinking = []
    tool_calls = []
    tool_results = []
    errors = []
    is_thinking = False
    for event in api.sse(f"/api/ai-advisor/sessions/{session_id}/messages/stream", {"content": question}):
        if isinstance(event, dict):
            kind = event.get("type")
            if kind == "tool_call":
                tool_calls.append({"name": event.get("name"), "args": event.get("args")})
            elif kind == "tool_result":
                tool_results.append({"name": event.get("name"), "success": event.get("success")})
            elif kind == "error":
                errors.append(event.get("message", "unknown error"))
            elif kind == "thinking_start":
                is_thinking = True
            elif kind == "thinking_end":
                is_thinking = False
        else:
            if is_thinking:
                thinking.append(str(event))
            else:
                answer.append(str(event))
    return "".join(answer), "".join(thinking), tool_calls, tool_results, errors


def score_case(case, answer: str, tool_calls, errors):
    lower = answer.lower()
    hits = []
    misses = []
    for expected in case.get("expect", []):
        if expected.lower() in lower:
            hits.append(expected)
        else:
            misses.append(expected)

    forbidden_hits = [term for term in case.get("forbid", []) if term.lower() in lower]
    required_tool = case.get("require_tool")
    tool_names = [call["name"] for call in tool_calls]
    tool_ok = required_tool is None or required_tool in tool_names

    total_checks = len(case.get("expect", [])) + len(case.get("forbid", [])) + (1 if required_tool else 0)
    passed_checks = len(hits) + (len(case.get("forbid", [])) - len(forbidden_hits)) + (1 if tool_ok and required_tool else 0)
    score = 1.0 if total_checks == 0 else passed_checks / total_checks
    passed = score >= 0.75 and not errors
    return {
        "score": round(score, 3),
        "passed": passed,
        "hits": hits,
        "misses": misses,
        "forbiddenHits": forbidden_hits,
        "requiredTool": required_tool,
        "toolNames": tool_names,
        "toolOk": tool_ok,
        "errors": errors,
    }


def write_reports(results, output_dir: Path, prefix: str | None = None):
    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = prefix or datetime.now().strftime("%Y%m%d_%H%M%S")
    json_path = output_dir / f"dio_rag_eval_{stamp}.json"
    md_path = output_dir / f"dio_rag_eval_{stamp}.md"
    passed = sum(1 for r in results if r["evaluation"]["passed"])
    avg = sum(r["evaluation"]["score"] for r in results) / max(1, len(results))

    json_path.write_text(json.dumps({
        "generatedAt": datetime.now().isoformat(),
        "passed": passed,
        "total": len(results),
        "averageScore": round(avg, 3),
        "results": results,
    }, indent=2), encoding="utf-8")

    lines = [
        "# Dio RAG Evaluation",
        "",
        f"- Passed: {passed}/{len(results)}",
        f"- Average heuristic score: {avg:.3f}",
        "",
    ]
    for result in results:
        ev = result["evaluation"]
        mark = "PASS" if ev["passed"] else "FAIL"
        lines.extend([
            f"## {mark} {result['id']} ({ev['score']})",
            "",
            f"**Question:** {result['question']}",
            "",
            f"**Tools:** {', '.join(ev['toolNames']) or 'none'}",
            "",
            f"**Hits:** {', '.join(ev['hits']) or 'none'}",
            "",
            f"**Misses:** {', '.join(ev['misses']) or 'none'}",
            "",
            "**Answer:**",
            "",
            result["answer"].strip() or "(empty)",
            "",
        ])
    md_path.write_text("\n".join(lines), encoding="utf-8")
    return json_path, md_path, passed, avg


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", default=os.environ.get("BACKEND_URL", "http://localhost:18080"))
    parser.add_argument("--output-dir", default="rag_eval_results")
    parser.add_argument("--no-warmup", action="store_true")
    parser.add_argument("--warmup-wait", type=int, default=75)
    parser.add_argument("--limit", type=int, default=None, help="Run only the first N questions.")
    parser.add_argument("--start", type=int, default=1, help="1-based question index to start from.")
    args = parser.parse_args()

    api = login(args.backend)
    session_id = create_session(api)
    output_dir = Path(args.output_dir)
    run_prefix = "partial"

    if not args.no_warmup:
        print("Warmup: triggering RAG document generation...", flush=True)
        answer, _, tools, _, errors = ask(api, session_id,
                                      "Please review my three-month wellness history and prepare context for future questions.")
        if errors:
            print(f"Warmup errors: {errors}", flush=True)
        print(f"Warmup answer length={len(answer)}, tools={[t['name'] for t in tools]}", flush=True)
        print(f"Waiting {args.warmup_wait}s for async RAG document generation...", flush=True)
        time.sleep(args.warmup_wait)

    results = []
    selected = QUESTIONS[args.start - 1:]
    if args.limit is not None:
        selected = selected[:args.limit]
    total = len(selected)
    for index, case in enumerate(selected, start=args.start):
        print(f"[{index}/{len(QUESTIONS)}] {case['id']}: {case['question']}", flush=True)
        started = time.time()
        answer, thinking, tool_calls, tool_results, errors = ask(api, session_id, case["question"])
        evaluation = score_case(case, answer, tool_calls, errors)
        elapsed = time.time() - started
        print(f"  score={evaluation['score']} passed={evaluation['passed']} "
              f"tools={evaluation['toolNames']} elapsed={elapsed:.1f}s", flush=True)
        results.append({
            "id": case["id"],
            "question": case["question"],
            "answer": answer,
            "thinkingLength": len(thinking),
            "toolCalls": tool_calls,
            "toolResults": tool_results,
            "evaluation": evaluation,
        })
        partial_json, partial_md, _, _ = write_reports(results, output_dir, prefix=run_prefix)
        print(f"  partial: {partial_json}", flush=True)

    json_path, md_path, passed, avg = write_reports(results, output_dir)
    print(f"Done. Passed {passed}/{len(results)}, average score {avg:.3f}", flush=True)
    print(f"JSON: {json_path}", flush=True)
    print(f"Markdown: {md_path}", flush=True)


if __name__ == "__main__":
    main()
