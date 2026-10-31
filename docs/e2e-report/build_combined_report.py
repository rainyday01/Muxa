#!/usr/bin/env python3
"""Build combined Round1+Round2 E2E HTML report with embedded screenshots."""
from __future__ import annotations

import base64
import html
import json
import mimetypes
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RUN1 = ROOT / "run"
SHOT1 = RUN1 / "screenshots"
PASS2 = RUN1 / "pass2"
RUN3 = ROOT / "run3"
RUN4 = ROOT / "run4"
OUT_DIR = ROOT / "combined"
OUT_DIR.mkdir(parents=True, exist_ok=True)
# also copy originals for relative-path browse
ASSETS = OUT_DIR / "assets"
ASSETS.mkdir(exist_ok=True)

# Round2 cases (tag-assisted host add + remote path)
R2_CASES = [
    {
        "id": "R2-B01",
        "maps_to": "TC-B01",
        "title": "添加密码主机（testTag 自动化）",
        "priority": "P0",
        "status": "Pass",
        "notes": "host_add/alias/hostname/username/password + 收起键盘滚动 host_save；列表出现 tencentvps；冷启动 PERSIST",
        "screenshots": [
            (RUN4, "001_home.png"),
            (RUN4, "002_hosts.png"),
            (RUN4, "003_editor.png"),
            (RUN4, "004_filled.png"),
            (RUN4, "005_ime_hidden.png"),
            (RUN4, "006_scrolled_to_save.png"),
            (RUN4, "007_after_save.png"),
            (RUN4, "008_hosts_list.png"),
            (RUN4, "027_reopen_hosts.png"),
        ],
    },
    {
        "id": "R2-B07",
        "maps_to": "TC-B07",
        "title": "known_hosts / 信任主机密钥",
        "priority": "P0",
        "status": "Pass",
        "notes": "对话框「未知主机密钥」→ 点「信任并继续」；toast 已信任并连接",
        "screenshots": [
            (RUN4, "009_probe.png"),
            (RUN4, "041_hk_start.png"),
            (RUN4, "042_hk_accepted_0.png"),
            (RUN4, "043_after_trust.png"),
        ],
    },
    {
        "id": "R2-B11",
        "maps_to": "TC-B11",
        "title": "环境检查 / 探测徽章",
        "priority": "P0",
        "status": "Pass",
        "notes": "环境检查后显示 tmux ✓ · mosh ✗ · AUTO · 404ms",
        "screenshots": [
            (RUN4, "043_after_trust.png"),
            (RUN4, "008_hosts_list.png"),
        ],
    },
    {
        "id": "R2-C01",
        "maps_to": "TC-C01",
        "title": "会话列表展示与刷新",
        "priority": "P0",
        "status": "Pass",
        "notes": "会话 Tab 列出 cliproxy / tvapk，带 pane 预览",
        "screenshots": [
            (RUN4, "044_sessions_tab.png"),
            (RUN4, "045_sessions.png"),
        ],
    },
    {
        "id": "R2-C13",
        "maps_to": "TC-C13",
        "title": "远程 tmux 会话可见（双 socket）",
        "priority": "P0",
        "status": "Pass",
        "notes": "远端已有 cliproxy、tvapk 会话均可见（桌面共用 VPS）",
        "screenshots": [
            (RUN4, "045_sessions.png"),
            (RUN4, "033_sessions.png"),
        ],
    },
    {
        "id": "R2-D01",
        "maps_to": "TC-D01",
        "title": "进入会话终端",
        "priority": "P0",
        "status": "Pass",
        "notes": "session_enter_cliproxy tag 进入；标题 cliproxy · SSH · SSH 40ms",
        "screenshots": [
            (RUN4, "045_sessions.png"),
            (RUN4, "046_terminal.png"),
            (RUN4, "034_terminal.png"),
        ],
    },
    {
        "id": "R2-D02",
        "maps_to": "TC-D02",
        "title": "基本输入输出",
        "priority": "P0",
        "status": "Pass",
        "notes": "终端已附着远端 cliproxy 内容；发送 echo e2e-pass4-ok",
        "screenshots": [
            (RUN4, "046_terminal.png"),
            (RUN4, "047_terminal_echo.png"),
            (RUN4, "035_terminal_echo.png"),
        ],
    },
    {
        "id": "R2-P03",
        "maps_to": "—",
        "title": "第一轮 tag 试验 pass3（未完整保存）",
        "priority": "P1",
        "status": "Fail",
        "notes": "表单填满但软键盘挡住保存按钮，after_save 仍停在编辑页；冷启动无主机",
        "screenshots": [
            (RUN3, "001_home.png"),
            (RUN3, "002_hosts.png"),
            (RUN3, "003_editor.png"),
            (RUN3, "004_filled.png"),
            (RUN3, "005_after_save.png"),
            (RUN3, "300_hosts_reopen.png"),
            (RUN3, "302_sessions.png"),
        ],
    },
    {
        "id": "R2-P02",
        "maps_to": "TC-B01",
        "title": "第二轮补测 pass2（无 tag，+ 未打开编辑页）",
        "priority": "P1",
        "status": "Fail",
        "notes": "Compose + 无障碍树，点 + 未进入编辑页",
        "screenshots": [
            (PASS2, "001_home.png"),
            (PASS2, "002_hosts.png"),
            (PASS2, "003_editor.png"),
            (PASS2, "004_filled.png"),
        ],
    },
]

# Fill R1 cases that had zero dedicated screenshots
R1_FALLBACK_SHOTS = {
    "TC-D01": ["024_TC-C13_dual_list.png", "018_TC-C01_list.png"],  # blocked: empty list context
    "TC-E07": ["033_TC-E06_settings.png"],
    "TC-E10": ["033_TC-E06_settings.png"],
    "TC-F05": ["034_TC-F01_settings2.png"],
    "TC-I04": ["035_TC-I01_appearance.png", "036_TC-I09_about.png"],
}


def b64_img(path: Path) -> str:
    if not path.is_file():
        return ""
    mime = mimetypes.guess_type(path.name)[0] or "image/png"
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{data}"


def copy_asset(src: Path, prefix: str) -> str:
    """Copy into assets/ and return relative name."""
    if not src.is_file():
        return ""
    name = f"{prefix}_{src.name}"
    dest = ASSETS / name
    if not dest.exists() or dest.stat().st_size != src.stat().st_size:
        dest.write_bytes(src.read_bytes())
    return name


def figure(src: Path, caption: str, embed: bool) -> str:
    if not src.is_file():
        return f'<p class="miss">缺失截图: {html.escape(caption)}</p>'
    cap = html.escape(caption)
    if embed:
        uri = b64_img(src)
        return (
            f'<figure><a href="{uri}" target="_blank">'
            f'<img src="{uri}" alt="{cap}" loading="lazy"/></a>'
            f"<figcaption>{cap}</figcaption></figure>"
        )
    # relative
    rel = copy_asset(src, "shot")
    return (
        f'<figure><a href="assets/{html.escape(rel)}" target="_blank">'
        f'<img src="assets/{html.escape(rel)}" alt="{cap}" loading="lazy"/></a>'
        f"<figcaption>{cap}</figcaption></figure>"
    )


def badge(status: str) -> str:
    return f'<span class="badge {html.escape(status.lower())}">{html.escape(status)}</span>'


def case_section(
    cid: str,
    title: str,
    priority: str,
    status: str,
    notes: str,
    duration: str,
    shots_html: str,
    extra: str = "",
) -> str:
    cls = {"Pass": "pass", "Fail": "fail", "Blocked": "blocked"}.get(status, "na")
    return f"""
<section class="case {cls}" id="{html.escape(cid)}">
  <h3>{badge(status)} {html.escape(cid)} · {html.escape(title)}
    <small>P={html.escape(priority)} · {html.escape(duration)}</small></h3>
  <p class="notes">{html.escape(notes)}</p>
  {extra}
  <div class="shots">{shots_html or '<p class="miss">无截图</p>'}</div>
</section>"""


def main():
    data = json.loads((RUN1 / "results.json").read_text(encoding="utf-8"))
    r1 = data["results"]

    # Final merged status
    r2_by_map = {c["maps_to"]: c for c in R2_CASES if c["maps_to"] != "—"}
    final_rows = []
    for r in r1:
        cid = r["id"]
        if cid in r2_by_map and r2_by_map[cid]["status"] == "Pass":
            final_rows.append(
                {
                    "id": cid,
                    "title": r["title"],
                    "priority": r["priority"],
                    "r1": r["status"],
                    "r2": r2_by_map[cid]["status"],
                    "final": "Pass",
                    "notes": f"R1: {r.get('notes','')}; R2: {r2_by_map[cid]['notes']}",
                }
            )
        else:
            final_rows.append(
                {
                    "id": cid,
                    "title": r["title"],
                    "priority": r["priority"],
                    "r1": r["status"],
                    "r2": r2_by_map[cid]["status"] if cid in r2_by_map else "—",
                    "final": r["status"],
                    "notes": r.get("notes") or "",
                }
            )

    final_pass = sum(1 for x in final_rows if x["final"] == "Pass")
    final_fail = sum(1 for x in final_rows if x["final"] == "Fail")
    final_block = sum(1 for x in final_rows if x["final"] == "Blocked")
    r1s = data["summary"]

    # Build with both embedded (for Feishu single file) and relative versions
    for embed, out_name in ((True, "index.html"), (False, "index-rel.html")):
        r1_sections = []
        r1_index_rows = []
        for r in r1:
            names = list(r.get("screenshots") or [])
            fallback_note = ""
            if not names and r["id"] in R1_FALLBACK_SHOTS:
                names = R1_FALLBACK_SHOTS[r["id"]]
                fallback_note = (
                    '<p class="notes warn">本用例自动化未单独截图，附同轮相关界面截图。</p>'
                )
            shots = "".join(
                figure(SHOT1 / n, f"R1 · {n}", embed) for n in names
            )
            if not names:
                # last resort: final smoke
                shots = figure(SHOT1 / "042_final.png", "R1 · 042_final.png（关联）", embed)
                fallback_note = (
                    '<p class="notes warn">无专用截图，使用本轮结束态截图。</p>'
                )
            r1_sections.append(
                case_section(
                    r["id"],
                    r["title"],
                    r["priority"],
                    r["status"],
                    r.get("notes") or "",
                    f"{r.get('duration_s', 0)}s",
                    shots,
                    extra=fallback_note,
                )
            )
            r1_index_rows.append(
                f"<tr><td><a href='#{html.escape(r['id'])}'>{html.escape(r['id'])}</a></td>"
                f"<td>{html.escape(r['title'])}</td><td>{html.escape(r['priority'])}</td>"
                f"<td>{badge(r['status'])}</td>"
                f"<td>{html.escape(r.get('notes') or '')}</td>"
                f"<td>{len(names) or 1}</td></tr>"
            )

        r2_sections = []
        r2_index_rows = []
        for c in R2_CASES:
            parts = []
            for folder, name in c["screenshots"]:
                p = folder / name
                parts.append(figure(p, f"{c['id']} · {name}", embed))
            r2_sections.append(
                case_section(
                    c["id"],
                    c["title"],
                    c["priority"],
                    c["status"],
                    c["notes"],
                    "—",
                    "".join(parts),
                    extra=(
                        f'<p class="notes">映射用例: '
                        f"<code>{html.escape(c['maps_to'])}</code></p>"
                    ),
                )
            )
            r2_index_rows.append(
                f"<tr><td><a href='#{html.escape(c['id'])}'>{html.escape(c['id'])}</a></td>"
                f"<td>{html.escape(c['title'])}</td>"
                f"<td>{html.escape(c['maps_to'])}</td>"
                f"<td>{badge(c['status'])}</td>"
                f"<td>{html.escape(c['notes'][:80])}</td>"
                f"<td>{len(c['screenshots'])}</td></tr>"
            )

        final_table = "".join(
            f"<tr><td><a href='#{html.escape(x['id'])}'>{html.escape(x['id'])}</a></td>"
            f"<td>{html.escape(x['title'])}</td>"
            f"<td>{badge(x['r1'])}</td><td>{html.escape(str(x['r2'])) if x['r2']=='—' else badge(str(x['r2']))}</td>"
            f"<td>{badge(x['final'])}</td>"
            f"<td>{html.escape(x['notes'][:100])}</td></tr>"
            for x in final_rows
        )

        doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>m-tmux E2E 两轮合并测试报告</title>
<style>
:root {{
  --bg:#0f1419; --card:#1a2332; --fg:#e7ecf3; --muted:#8b9bb4;
  --pass:#3dd68c; --fail:#f07178; --blocked:#ffcc66; --na:#8899aa;
}}
* {{ box-sizing:border-box; }}
body {{ margin:0; font-family: ui-sans-serif, system-ui, "Segoe UI", "PingFang SC", "Noto Sans SC", sans-serif;
  background:var(--bg); color:var(--fg); line-height:1.55; }}
header {{ padding:2rem 1.5rem; background:linear-gradient(135deg,#1a2332,#0d3b4c 60%,#1a2a1a);
  border-bottom:1px solid #2a3a4f; }}
header h1 {{ margin:0 0 .5rem; font-size:1.55rem; }}
header p {{ margin:.25rem 0; color:var(--muted); font-size:.95rem; }}
.summary {{ display:flex; gap:.75rem; flex-wrap:wrap; margin-top:1rem; }}
.summary .box {{ background:rgba(0,0,0,.25); padding:.7rem 1rem; border-radius:10px; min-width:5.5rem; }}
.summary .n {{ font-size:1.55rem; font-weight:700; }}
nav.toc {{ background:var(--card); margin:1rem 1.5rem 0; padding:1rem 1.2rem; border-radius:12px; max-width:1100px; }}
nav.toc a {{ color:#6cb6ff; margin-right:1rem; }}
main {{ max-width:1100px; margin:0 auto; padding:1.5rem; }}
h2 {{ margin-top:2rem; border-bottom:1px solid #2a3a4f; padding-bottom:.4rem; }}
.case {{ background:var(--card); border-radius:12px; padding:1rem 1.2rem; margin-bottom:1rem; border-left:4px solid #445; }}
.case.pass {{ border-left-color:var(--pass); }}
.case.fail {{ border-left-color:var(--fail); }}
.case.blocked {{ border-left-color:var(--blocked); }}
.badge {{ display:inline-block; padding:.12rem .5rem; border-radius:6px; font-size:.72rem; font-weight:700; background:#333; vertical-align:middle; }}
.badge.pass {{ background:var(--pass); color:#042; }}
.badge.fail {{ background:var(--fail); color:#300; }}
.badge.blocked {{ background:var(--blocked); color:#330; }}
.notes {{ color:var(--muted); font-size:.92rem; }}
.notes.warn {{ color:var(--blocked); }}
.shots {{ display:flex; flex-wrap:wrap; gap:.75rem; margin-top:.75rem; }}
figure {{ margin:0; width:200px; }}
@media (min-width:900px) {{ figure {{ width:220px; }} }}
img {{ width:100%; border-radius:8px; border:1px solid #334; background:#000; display:block; }}
figcaption {{ font-size:.68rem; color:var(--muted); word-break:break-all; margin-top:.25rem; }}
table {{ width:100%; border-collapse:collapse; margin:1rem 0; font-size:.88rem; }}
th,td {{ text-align:left; padding:.45rem .55rem; border-bottom:1px solid #2a3a4f; vertical-align:top; }}
th {{ color:var(--muted); font-weight:600; }}
a {{ color:#6cb6ff; text-decoration:none; }}
a:hover {{ text-decoration:underline; }}
.miss {{ color:var(--fail); font-size:.85rem; }}
.callout {{ background:#132a20; border:1px solid #2a5; border-radius:10px; padding:1rem 1.2rem; margin:1rem 0; }}
.callout.failish {{ background:#2a1518; border-color:#833; }}
footer {{ padding:2rem; text-align:center; color:var(--muted); font-size:.85rem; }}
code {{ background:#0a1018; padding:.1rem .35rem; border-radius:4px; font-size:.85em; }}
</style>
</head>
<body>
<header>
  <h1>m-tmux 全功能 E2E 测试报告（两轮合并）</h1>
  <p>App: <code>app.mtmux.android</code> · 被测包约 <code>0.1.8-e2e-tags</code>（第二轮） / 同功能树（第一轮）</p>
  <p>执行机: elli（Ubuntu + Android Emulator API_34）· VPS: {html.escape(str(data.get('vps_host') or ''))} · 日期: 2026-07-16</p>
  <p>驱动: adb + uiautomator · 第二轮增加 Compose <code>testTag</code>（host_add / host_save / session_enter_*）</p>
  <div class="summary">
    <div class="box"><div class="n">{len(final_rows)}</div>合并用例</div>
    <div class="box"><div class="n" style="color:var(--pass)">{final_pass}</div>最终 Pass</div>
    <div class="box"><div class="n" style="color:var(--fail)">{final_fail}</div>最终 Fail</div>
    <div class="box"><div class="n" style="color:var(--blocked)">{final_block}</div>最终 Blocked</div>
    <div class="box"><div class="n">{r1s['total']}</div>R1 执行</div>
    <div class="box"><div class="n" style="color:var(--pass)">{r1s['pass']}</div>R1 Pass</div>
    <div class="box"><div class="n" style="color:var(--fail)">{r1s['fail']}</div>R1 Fail</div>
    <div class="box"><div class="n" style="color:var(--blocked)">{r1s['blocked']}</div>R1 Blocked</div>
    <div class="box"><div class="n">{len(R2_CASES)}</div>R2 专项</div>
  </div>
</header>

<nav class="toc">
  <strong>目录</strong>
  <a href="#final">合并结论</a>
  <a href="#r1">第一轮详情</a>
  <a href="#r2">第二轮详情</a>
  <a href="#env">环境说明</a>
</nav>

<main>
<div class="callout" id="env">
  <strong>环境与约束</strong>
  <ul>
    <li>elli 上 Android 模拟器（headless AVD API_34）；未改动主机 X11/DISPLAY 配置。</li>
    <li>测试 VPS: tencentvps / ubuntu@198.51.100.143:22；远端已有 tmux 会话 cliproxy、tvapk。</li>
    <li>第一轮阻塞点：Compose「+」/ 表单在 uiautomator 中难定位 → 无法添加主机 → 远端会话链路 Blocked/Fail。</li>
    <li>第二轮：App 增加 testTag + 脚本收起软键盘后点 host_save；主机密钥按钮文案为「信任并继续」。</li>
  </ul>
</div>

<h2 id="final">合并结论表（R2 成功则覆盖 R1 同 ID）</h2>
<div class="callout">
  第二轮在 testTag 帮助下打通：<strong>添加主机 → 信任密钥 → 列会话 → 进入 cliproxy 终端（SSH 40ms）</strong>。
  仍失败/未充分覆盖：TC-C05 新建空白会话、TC-E06 键盘布局入口等。
</div>
<table>
  <tr><th>ID</th><th>标题</th><th>R1</th><th>R2</th><th>最终</th><th>备注</th></tr>
  {final_table}
</table>

<h2 id="r1">第一轮 · 全量自动化（{r1s['total']} 用例）</h2>
<p class="notes">时间: {html.escape(data.get('started',''))} · Pass {r1s['pass']} / Fail {r1s['fail']} / Blocked {r1s['blocked']}</p>
<table>
  <tr><th>ID</th><th>标题</th><th>优先级</th><th>结果</th><th>备注</th><th>截图数</th></tr>
  {''.join(r1_index_rows)}
</table>
{''.join(r1_sections)}

<h2 id="r2">第二轮 · testTag 主机与远端主路径（{len(R2_CASES)} 专项）</h2>
<p class="notes">含 pass2（无 tag 失败对照）、pass3（填表未保存）、pass4（完整通过）截图。</p>
<table>
  <tr><th>ID</th><th>标题</th><th>映射</th><th>结果</th><th>备注</th><th>截图数</th></tr>
  {''.join(r2_index_rows)}
</table>
{''.join(r2_sections)}

</main>
<footer>
  基于 docs/E2E-TEST-CASES.md · 技能 android-apk-e2e · 报告生成 build_combined_report.py<br/>
  截图路径: run/screenshots（R1）、run/pass2、run3、run4（R2）· {"内嵌 Base64 单文件" if embed else "相对路径 assets/"}
</footer>
</body>
</html>
"""
        out = OUT_DIR / out_name
        out.write_text(doc, encoding="utf-8")
        print("WROTE", out, "bytes", out.stat().st_size)

    # also write machine-readable summary
    summary = {
        "final": {
            "total": len(final_rows),
            "pass": final_pass,
            "fail": final_fail,
            "blocked": final_block,
        },
        "round1": r1s,
        "round2_specials": len(R2_CASES),
        "cases": final_rows,
    }
    (OUT_DIR / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("WROTE", OUT_DIR / "summary.json")


if __name__ == "__main__":
    main()
