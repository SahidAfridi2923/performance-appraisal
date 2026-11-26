#!/usr/bin/env python3
"""End-to-end API check against a running backend that was started with SEED_DEMO_USERS=true on an empty DB.

    python3 tools/api_smoke_test.py [http://localhost:8080]

Walks the whole flow: question bank edits -> open cycle -> Self/Peer/TL/Manager reviews -> scores
(must equal the CA sheet sample: 7.7 / 7.3 / 7.46 Exceeds Expectations) -> sync -> achievements -> close.
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")


def call(method, path, token=None, body=None, expect=200):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    data = json.loads(raw) if raw else None
    if status != expect:
        raise AssertionError(f"{method} {path}: expected {expect}, got {status}: {data}")
    return data


def login(user, pw="password123"):
    return call("POST", "/api/auth/login", body={"username": user, "password": pw})["token"]


def check(cond, msg):
    if not cond:
        raise AssertionError(msg)
    print("  ok -", msg)


# the admin user is created right after startup; wait for it
for _ in range(30):
    try:
        admin = login("admin", "admin123")
        break
    except AssertionError:
        time.sleep(1)
else:
    raise SystemExit("admin login never succeeded - is the backend running on an empty database?")
mgr, lead, sara, jay = (login(u) for u in ("manager", "lead", "sara", "jay"))

print("question bank")
teams = call("GET", "/api/admin/teams", admin)
ca = next(t for t in teams if t["code"] == "CA")
bank = call("GET", f"/api/admin/question-bank?teamId={ca['id']}", admin)
n_common = sum(len(c["questions"]) for c in bank["common"])
n_role = sum(len(c["questions"]) for c in bank["role"])
check(n_common == 53 and n_role == 11, f"CA bank has 53 common + 11 role questions ({n_common}+{n_role})")
call("GET", "/api/admin/teams", jay, expect=403)
check(True, "non-admin cannot reach admin API")

# settings validation
call("PUT", "/api/admin/settings", admin, {**bank["settings"], "commonWeight": 0.5}, expect=400)
check(True, "weights must total 100%")

print("open cycle")
cycle = call("POST", "/api/admin/cycles", admin, {"name": "Jul-Sep 2026"})
cycle = call("POST", f"/api/admin/cycles/{cycle['id']}/open", admin)
check(cycle["status"] == "OPEN" and cycle["appraisalCount"] == 3, "cycle opened with 3 appraisals (lead, sara, jay)")
cid = cycle["id"]

# editing the bank after opening must not change the open cycle
role_cat = bank["role"][0]
newq = call("POST", "/api/admin/questions", admin, {"categoryId": role_cat["id"], "text": "How well does the employee mentor new CAs?",
                                                    "weight": 1, "reviewedBy": "ALL", "sortOrder": 0, "active": True})

tasks = call("GET", f"/api/cycles/{cid}/tasks", sara)
sara_app = tasks["myAppraisalId"]
form = call("GET", f"/api/appraisals/{sara_app}/reviews/SELF", sara)
qids = [q["id"] for c in form["categories"] for q in c["questions"]]
check(len(qids) == 52, f"Senior self form shows 52 questions (no Team lead level, new question not in cycle) [{len(qids)}]")
role_ids = {q["id"] for c in form["categories"] if c["block"] == "ROLE" for q in c["questions"]}


def answers(common, role, comment=None):
    return {"answers": [{"questionId": q, "value": role if q in role_ids else common, "na": False,
                         "comment": comment} for q in qids]}


print("reviews")
call("PUT", f"/api/appraisals/{sara_app}/reviews/SELF", sara, {"answers": [{"questionId": qids[0], "value": 9, "na": False}]})
call("POST", f"/api/appraisals/{sara_app}/reviews/SELF/submit", sara, {"answers": []}, expect=400)
check(True, "incomplete self review cannot be submitted (draft kept)")
call("POST", f"/api/appraisals/{sara_app}/reviews/SELF/submit", sara, answers(9, 8))
call("POST", f"/api/appraisals/{sara_app}/reviews/PEER", jay, answers(8, 8), expect=405)
call("POST", f"/api/appraisals/{sara_app}/reviews/PEER/submit", jay, answers(8, 8))
call("PUT", f"/api/appraisals/{sara_app}/reviews/SELF", sara, answers(1, 1), expect=400)
check(True, "submitted review is locked")
call("POST", f"/api/appraisals/{sara_app}/reviews/TL/submit", jay, answers(8, 7), expect=403)
check(True, "employee cannot review as TL")

summary = call("GET", f"/api/cycles/{cid}/summary", mgr)
row = next(r for r in summary["rows"] if r["employeeName"] == "Sara Senior")
check(row["status"] == "INCOMPLETE" and row["finalScore"] is None, "no final score before TL/Manager answer")

call("POST", f"/api/appraisals/{sara_app}/reviews/TL/submit", lead, answers(9, 7), expect=400)
check(True, "TL answer of 9 without a comment is rejected")
call("POST", f"/api/appraisals/{sara_app}/reviews/TL/submit", lead, answers(8, 7))
call("POST", f"/api/appraisals/{sara_app}/reviews/MANAGER/submit", mgr, answers(7, 7))

summary = call("GET", f"/api/cycles/{cid}/summary", mgr)
row = next(r for r in summary["rows"] if r["employeeName"] == "Sara Senior")
check((row["commonScore"], row["roleScore"], row["finalScore"], row["rating"]) == (7.7, 7.3, 7.46, "Exceeds Expectations"),
      f"scores match the CA sheet: {row['commonScore']} / {row['roleScore']} / {row['finalScore']} {row['rating']}")
check(row["leadAnswered"] == 52 and row["needed"] == 52, "52 of 52 answered by TL/Manager")

tl_app = next(t for t in call("GET", f"/api/cycles/{cid}/tasks", mgr)["tasks"] if t["employeeName"] == "Leo Lead")["appraisalId"]
call("GET", f"/api/appraisals/{tl_app}/reviews/TL", lead, expect=403)
form_tl = call("GET", f"/api/appraisals/{tl_app}/reviews/MANAGER", mgr)
check(sum(len(c["questions"]) for c in form_tl["categories"]) == 64, "Team Lead form has 64 questions incl. Team lead level")

print("results visibility")
call("GET", f"/api/appraisals/{sara_app}", jay, expect=403)
own = call("GET", f"/api/appraisals/{sara_app}", sara)
check(own["result"] is None, "employee does not see own scores while the cycle is open")
detail = call("GET", f"/api/appraisals/{sara_app}", mgr)
check(detail["result"]["finalScore"] == 7.46, "manager sees the result breakdown")

print("achievements")
ach = call("POST", f"/api/appraisals/{sara_app}/achievements", sara,
           {"text": "Delivered the alerts requirement package", "situation": "Owned start to finish",
            "businessImpact": "Medium - product or team delivery", "verified": True})
check(not ach["verified"], "employee cannot self-verify")
ach = call("PUT", f"/api/achievements/{ach['id']}", lead, {**ach, "verified": True})
check(ach["verified"], "lead verifies")
ach = call("PUT", f"/api/achievements/{ach['id']}", sara, {**ach, "text": "Delivered the alerts package early"})
check(not ach["verified"], "employee edit resets verification")
call("POST", f"/api/appraisals/{sara_app}/goals", sara, {"text": "Raise requirement documents passing the checklist",
                                                         "successCriteria": "9 of 10 documents pass"})

print("sync question edits into the open cycle")
preview = call("POST", f"/api/admin/cycles/{cid}/sync", admin)
check(preview["questionsAdded"] == 1 and not preview["applied"], f"preview: 1 question to add ({preview['notes']})")
form = call("GET", f"/api/appraisals/{sara_app}/reviews/SELF", sara)
check(sum(len(c["questions"]) for c in form["categories"]) == 52, "preview did not change the cycle")
applied = call("POST", f"/api/admin/cycles/{cid}/sync?apply=true", admin)
check(applied["applied"] and applied["questionsAdded"] == 1, "sync applied")
row = next(r for r in call("GET", f"/api/cycles/{cid}/summary", mgr)["rows"] if r["employeeName"] == "Sara Senior")
check(row["status"] == "INCOMPLETE" and row["needed"] == 53, "new question makes the score incomplete until answered")
mgr_review = next(r for r in call("GET", f"/api/admin/cycles/{cid}/reviews", admin) if r["role"] == "MANAGER" and r["employeeName"] == "Sara Senior")
call("POST", f"/api/admin/reviews/{mgr_review['id']}/reopen", admin)
form = call("GET", f"/api/appraisals/{sara_app}/reviews/MANAGER", mgr)
qids = [q["id"] for c in form["categories"] for q in c["questions"]]
role_ids = {q["id"] for c in form["categories"] if c["block"] == "ROLE" for q in c["questions"]}
call("POST", f"/api/appraisals/{sara_app}/reviews/MANAGER/submit", mgr, answers(7, 7))
row = next(r for r in call("GET", f"/api/cycles/{cid}/summary", mgr)["rows"] if r["employeeName"] == "Sara Senior")
check(row["status"] == "SCORED", f"scored again after manager answered the new question ({row['finalScore']})")

# archive the new question in the bank; the open cycle keeps it until synced
call("DELETE", f"/api/admin/questions/{newq['id']}", admin)
call("DELETE", f"/api/admin/questions/{newq['id']}?permanent=true", admin, expect=409)
check(True, "a question used in a cycle can be archived but not permanently deleted")

print("close")
call("POST", f"/api/admin/cycles/{cid}/close", admin)
mine = call("GET", "/api/my/appraisals", sara)
check(mine[0]["finalScore"] is not None and mine[0]["rating"], f"employee sees own result after close: {mine[0]['finalScore']} {mine[0]['rating']}")
call("PUT", f"/api/appraisals/{sara_app}/reviews/PEER", jay, {"answers": []}, expect=400)
check(True, "nothing is editable after close")
print("\nALL CHECKS PASSED")
