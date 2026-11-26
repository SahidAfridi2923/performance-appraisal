"""Extract the question bank and scoring rules from Performance_Appraisal_All_Teams.xlsx
into backend/src/main/resources/seed/seed-data.json (used to seed an empty database).
Usage: python3 tools/extract_seed.py <workbook.xlsx> <out.json>"""
import sys, json, openpyxl

wb = openpyxl.load_workbook(sys.argv[1], data_only=False)
sr = wb['Scoring_Rules']
levels = [sr.cell(19, c).value for c in (2, 3, 4)]

TEAMS = [("CA", "CA (Clinical Analyst)"), ("Design_UIUX", "Design (UI/UX)"), ("API_Dev", "API Development"),
         ("UI_Dev", "UI Development"), ("QA", "QA"), ("Mobile_Dev", "Mobile App Development"),
         ("AWS", "AWS"), ("Marketing", "Marketing"), ("AI_Team", "AI")]
CODES = {"CA": "CA", "Design_UIUX": "DESIGN", "API_Dev": "API", "UI_Dev": "UI", "QA": "QA",
         "Mobile_Dev": "MOBILE", "AWS": "AWS", "Marketing": "MARKETING", "AI_Team": "AI"}

# guidance from Metric_Library, keyed by question text
ml = wb['Metric_Library']
guidance = {}
for r in range(4, ml.max_row + 1):
    q = ml.cell(r, 4).value
    if not q:
        continue
    parts = []
    for label, col in (("Scale", 7), ("Evidence", 8), ("How to decide", 9)):
        v = ml.cell(r, col).value
        if v and str(v).strip() not in ("-", ""):
            parts.append(f"{label}: {v}")
    guidance.setdefault((ml.cell(r, 2).value, q.strip()), "\n".join(parts))
    guidance.setdefault(("*", q.strip()), "\n".join(parts))

cat_points = {}
for r in range(20, 27):
    cat_points[sr.cell(r, 1).value] = {levels[i]: sr.cell(r, 2 + i).value for i in range(3)}

def reviewed_by(v):
    return "TL_MANAGER_ONLY" if v and "TL / Manager" in v else "ALL"

def read_sheet(name, team_name):
    ws = wb[name]
    common, role, cur, block = [], [], None, None
    for r in range(8, ws.max_row + 1):
        a, b = ws.cell(r, 1).value, ws.cell(r, 2).value
        if isinstance(a, str) and a.startswith("COMMON COMPETENCIES"):
            block = "COMMON"; continue
        if isinstance(a, str) and a.startswith("ROLE-SPECIFIC"):
            block = "ROLE"; cur = {"name": "Role-specific", "points": {l: 1 for l in levels}, "questions": []}
            role.append(cur); continue
        if isinstance(b, str) and b.startswith("Answers entered"):
            break
        if block == "COMMON" and isinstance(a, str) and b is None:
            nm = a.replace("  [Team Leads only]", "").strip()
            cur = {"name": nm, "points": cat_points[nm], "questions": []}
            common.append(cur); continue
        if isinstance(a, (int, float)) and b:
            g = guidance.get((team_name, b.strip())) or guidance.get(("*", b.strip())) or ""
            cur["questions"].append({"text": b.strip(), "weight": float(ws.cell(r, 4).value),
                                     "reviewedBy": reviewed_by(ws.cell(r, 3).value), "guidance": g})
    return common, role

out = {"levels": [{"name": l, "skipTlReview": l == "Team Lead"} for l in levels],
       "settings": {"commonWeight": sr['B5'].value, "roleWeight": sr['B6'].value,
                    "selfWeight": sr['B12'].value, "peerWeight": sr['B13'].value,
                    "tlWeight": sr['B14'].value, "managerWeight": sr['B15'].value},
       "ratingBands": [{"label": sr.cell(r, 1).value, "minScore": sr.cell(r, 2).value} for r in range(63, 68)],
       "scaleLabels": [{"value": sr.cell(r, 2).value, "label": sr.cell(r, 1).value} for r in range(42, 52)],
       "pickLists": {"SITUATION": [sr.cell(r, 1).value for r in range(70, 75)],
                     "BUSINESS_IMPACT": [sr.cell(r, 1).value for r in range(77, 80)]},
       "teams": []}
common_ref = None
for sheet, tname in TEAMS:
    common, role = read_sheet(sheet, tname)
    if common_ref is None:
        common_ref = common
        out["commonCategories"] = common
    else:
        assert [q["text"] for c in common for q in c["questions"]] == [q["text"] for c in common_ref for q in c["questions"]], sheet
    out["teams"].append({"code": CODES[sheet], "name": tname, "roleCategories": role})

json.dump(out, open(sys.argv[2], "w"), indent=1, ensure_ascii=False)
print("common categories:", [(c["name"], len(c["questions"])) for c in out["commonCategories"]])
for t in out["teams"]:
    print(t["name"], [(c["name"], len(c["questions"]), round(sum(q['weight'] for q in c['questions']),2), {q['reviewedBy'] for q in c['questions']}) for c in t["roleCategories"]])
print("with guidance:", sum(1 for c in out["commonCategories"] for q in c["questions"] if q["guidance"]),
      sum(1 for t in out["teams"] for c in t["roleCategories"] for q in c["questions"] if q["guidance"]))
