#!/usr/bin/env python3
"""Generate HTML report from results.json + screenshots/."""
from __future__ import annotations

import base64
import html
import json
import sys
from pathlib import Path


def img_tag(shot_dir: Path, name: str) -> str:
    p = shot_dir / name
    if not p.is_file():
        return f'<p class="miss">missing {html.escape(name)}</p>'
    # embed as file relative path for portability
    return (
        f'<figure><a href="screenshots/{html.escape(name)}" target="_blank">'
        f'<img src="screenshots/{html.escape(name)}" alt="{html.escape(name)}" loading="lazy"/>'
        f"</a><figcaption>{html.escape(name)}</figcaption></figure>"
    )


def main():
    run_dir = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    data = json.loads((run_dir / "results.json").read_text(encoding="utf-8"))
    shot_dir = run_dir / "screenshots"
    s = data["summary"]
    rows = []
    for r in data["results"]:
        st = r["status"]
        cls = {"Pass": "pass", "Fail": "fail", "Blocked": "blocked"}.get(st, "na")
        shots_html = "".join(img_tag(shot_dir, n) for n in r.get("screenshots") or [])
        rows.append(
            f"""
<section class="case {cls}" id="{html.escape(r['id'])}">
  <h3><span class="badge">{html.escape(st)}</span> {html.escape(r['id'])} · {html.escape(r['title'])}
  <small>P={html.escape(r['priority'])} · {r['duration_s']}s</small></h3>
  <p class="notes">{html.escape(r.get('notes') or '')}</p>
  <div class="shots">{shots_html or '<p>无截图</p>'}</div>
</section>"""
        )

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>m-tmux E2E 测试报告</title>
<style>
:root {{ --bg:#0f1419; --card:#1a2332; --fg:#e7ecf3; --muted:#8b9bb4; --pass:#3dd68c; --fail:#f07178; --blocked:#ffcc66; }}
* {{ box-sizing:border-box; }}
body {{ margin:0; font-family: ui-sans-serif, system-ui, "Segoe UI", sans-serif; background:var(--bg); color:var(--fg); line-height:1.5; }}
header {{ padding:2rem 1.5rem; background:linear-gradient(135deg,#1a2332,#0d3b4c); border-bottom:1px solid #2a3a4f; }}
header h1 {{ margin:0 0 .5rem; font-size:1.6rem; }}
header p {{ margin:.25rem 0; color:var(--muted); }}
.summary {{ display:flex; gap:1rem; flex-wrap:wrap; margin-top:1rem; }}
.summary .box {{ background:var(--card); padding:.75rem 1.1rem; border-radius:10px; min-width:6rem; }}
.summary .n {{ font-size:1.6rem; font-weight:700; }}
main {{ max-width:1100px; margin:0 auto; padding:1.5rem; }}
.case {{ background:var(--card); border-radius:12px; padding:1rem 1.2rem; margin-bottom:1rem; border-left:4px solid #445; }}
.case.pass {{ border-left-color:var(--pass); }}
.case.fail {{ border-left-color:var(--fail); }}
.case.blocked {{ border-left-color:var(--blocked); }}
.badge {{ display:inline-block; padding:.1rem .5rem; border-radius:6px; font-size:.75rem; font-weight:700; background:#333; }}
.pass .badge {{ background:var(--pass); color:#042; }}
.fail .badge {{ background:var(--fail); color:#300; }}
.blocked .badge {{ background:var(--blocked); color:#330; }}
.notes {{ color:var(--muted); font-size:.92rem; }}
.shots {{ display:flex; flex-wrap:wrap; gap:.75rem; margin-top:.75rem; }}
figure {{ margin:0; width:220px; }}
img {{ width:100%; border-radius:8px; border:1px solid #334; background:#000; }}
figcaption {{ font-size:.7rem; color:var(--muted); word-break:break-all; }}
table {{ width:100%; border-collapse:collapse; margin:1rem 0; }}
th,td {{ text-align:left; padding:.4rem .6rem; border-bottom:1px solid #2a3a4f; font-size:.9rem; }}
a {{ color:#6cb6ff; }}
footer {{ padding:2rem; text-align:center; color:var(--muted); font-size:.85rem; }}
</style>
</head>
<body>
<header>
  <h1>m-tmux 全功能 E2E 测试报告</h1>
  <p>App: {html.escape(data.get('app_id',''))} · 生成时间: {html.escape(data.get('started',''))}</p>
  <p>测试 VPS: {html.escape(str(data.get('vps_host') or ''))} · 执行机: elli (Ubuntu + Android Emulator)</p>
  <div class="summary">
    <div class="box"><div class="n">{s['total']}</div>Total</div>
    <div class="box"><div class="n" style="color:var(--pass)">{s['pass']}</div>Pass</div>
    <div class="box"><div class="n" style="color:var(--fail)">{s['fail']}</div>Fail</div>
    <div class="box"><div class="n" style="color:var(--blocked)">{s['blocked']}</div>Blocked</div>
  </div>
</header>
<main>
  <h2>用例索引</h2>
  <table>
    <tr><th>ID</th><th>标题</th><th>优先级</th><th>结果</th><th>备注</th></tr>
    {''.join(
        f"<tr><td><a href='#{html.escape(r['id'])}'>{html.escape(r['id'])}</a></td>"
        f"<td>{html.escape(r['title'])}</td><td>{html.escape(r['priority'])}</td>"
        f"<td>{html.escape(r['status'])}</td><td>{html.escape(r.get('notes') or '')}</td></tr>"
        for r in data['results']
    )}
  </table>
  <h2>图文详情</h2>
  {''.join(rows)}
</main>
<footer>
  基于 docs/E2E-TEST-CASES.md · 技能 android-apk-e2e · 自动化驱动 adb+uiautomator<br/>
  部分用例因模拟器/Compose 无障碍树限制为尽力执行，详见备注。
</footer>
</body>
</html>
"""
    out = run_dir / "index.html"
    out.write_text(doc, encoding="utf-8")
    print("WROTE", out)


if __name__ == "__main__":
    main()
