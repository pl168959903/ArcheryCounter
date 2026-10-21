package com.zgrcc.archeryassistant

/** 網頁報告的樣式（v1.16）。純 CSS，沒有腳本、沒有外部資源；支援淺色／深色與列印。 */
internal object ReportStyle {
    val CSS: String = """
:root{--bg:#eef3f5;--card:#ffffff;--ink:#14262e;--muted:#5d727b;--line:#dbe4e8;--brand:#1f4452;--brand2:#2d6a7f;--accent:#f2c230;--good:#2e7d32;--bad:#c62828;--chip:#e6eef1;--bar:#2d6a7f;--line1:#2d6a7f;--line2:#c98a00}
@media (prefers-color-scheme:dark){:root{--bg:#0d181d;--card:#15252c;--ink:#e6eef1;--muted:#93a8b0;--line:#26404a;--brand:#0f2a33;--brand2:#3c8aa3;--chip:#1d333c;--bar:#4aa0bb;--line1:#5ab4cf;--line2:#f2c230;--good:#66bb6a;--bad:#ef6c6c}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.6 -apple-system,"Segoe UI","Noto Sans TC","PingFang TC","Microsoft JhengHei",Roboto,sans-serif}
.wrap{max-width:980px;margin:0 auto;padding:0 16px 48px}
.hero{background:linear-gradient(135deg,var(--brand),var(--brand2));color:#fff;padding:28px 0 26px}
.hero .wrap{display:flex;gap:20px;align-items:center;padding-bottom:0}
.hero img{width:92px;height:92px;border-radius:50%;border:3px solid rgba(255,255,255,.85);box-shadow:0 6px 18px rgba(0,0,0,.35);background:#0b2530;flex:none}
.hero h1{margin:0;font-size:28px;letter-spacing:.3px}
.hero .sub{margin:2px 0 10px;opacity:.88}
.badges{display:flex;flex-wrap:wrap;gap:8px}
.badge{background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.28);border-radius:999px;padding:2px 12px;font-size:13px;white-space:nowrap}
.badge b{color:var(--accent);font-weight:600}
h2{font-size:20px;margin:34px 0 12px;display:flex;align-items:center;gap:8px}
h2:before{content:"";width:5px;height:20px;border-radius:3px;background:var(--accent)}
h3{font-size:16px;margin:22px 0 8px;color:var(--muted);font-weight:600}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin:12px 0;box-shadow:0 1px 3px rgba(0,0,0,.05)}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px;margin:14px 0}
.kpi{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 14px}
.kpi .v{font-size:24px;font-weight:700;color:var(--brand2)}
.kpi .l{font-size:12.5px;color:var(--muted)}
table{border-collapse:collapse;width:100%;font-size:14px}
th,td{padding:6px 10px;text-align:right;border-bottom:1px solid var(--line);white-space:nowrap}
th:first-child,td:first-child,td.l,th.l{text-align:left}
th{color:var(--muted);font-weight:600;font-size:12.5px;background:var(--chip)}
tr.cancel td{color:var(--muted);text-decoration:line-through}
.scroll{overflow-x:auto}
a{color:var(--brand2);text-decoration:none}
a:hover{text-decoration:underline}
.tag{display:inline-block;background:var(--chip);border-radius:6px;padding:0 8px;font-size:12.5px;margin-right:6px}
.tag.match{background:var(--accent);color:#3a2d00}
.tag.cal{background:#7a5af8;color:#fff}
.star{color:#e0a800;font-weight:700}
.note{color:var(--muted);font-size:13px;margin:6px 0}
.ok{color:var(--good)}.bad{color:var(--bad)}
.sess{scroll-margin-top:12px}
.sess .head{display:flex;justify-content:space-between;align-items:baseline;flex-wrap:wrap;gap:6px}
.sess .head .t{font-size:18px;font-weight:700}
.chart{width:100%;height:auto;display:block;margin:6px 0}
.chart text{fill:var(--muted);font-size:11px}
.chart .grid{stroke:var(--line);stroke-width:1}
.chart .ln1{fill:none;stroke:var(--line1);stroke-width:2}
.chart .ln2{fill:none;stroke:var(--line2);stroke-width:2}
.chart .pt{fill:var(--line1)}
.chart .good{fill:var(--good)}.chart .badp{fill:var(--bad)}
.chart .bar{fill:var(--bar)}
details{margin:10px 0}
summary{cursor:pointer;color:var(--brand2);font-weight:600}
footer{margin-top:44px;padding-top:18px;border-top:1px solid var(--line);color:var(--muted);font-size:13px;text-align:center}
footer img{width:34px;height:34px;border-radius:50%;vertical-align:middle;margin-right:8px}
@media (max-width:560px){.hero .wrap{flex-direction:column;text-align:center}.hero h1{font-size:24px}.badges{justify-content:center}}
@media print{body{background:#fff}.hero{background:#1f4452!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}.card,.kpi{box-shadow:none;break-inside:avoid}details{display:block}details>summary{display:none}}
""".trimIndent()
}
