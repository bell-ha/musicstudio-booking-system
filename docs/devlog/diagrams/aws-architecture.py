#!/usr/bin/env python3
"""마디 AWS 아키텍처 그림(aws-architecture.html)을 만든다.

아이콘은 내려받아 그림 안에 넣는다(저장소에는 아이콘 파일을 두지 않는다).
    mkdir -p /tmp/icons && cd /tmp/icons && npm i aws-icons simple-icons
    python3 docs/devlog/diagrams/aws-architecture.py /tmp/icons/node_modules
AWS 아이콘: AWS Architecture Icons(아키텍처 그림용 공식 아이콘), 나머지: Simple Icons(CC0).
"""
import base64, json, re, subprocess, sys
from pathlib import Path

NM = Path(sys.argv[1] if len(sys.argv) > 1 else "node_modules")
OUT = Path(__file__).with_name("aws-architecture.html")


def aws(name, kind="architecture-service"):
    return (NM / "aws-icons/icons" / kind / f"{name}.svg").read_text()


SI = json.loads(subprocess.check_output(["node", "-e",
    "const s=require('simple-icons');const o={};for(const k of ['siSpringboot','siReact','siNginx','siPostgresql','siDocker','siGoogle','siGithubactions','siTerraform','siOpenjdk','siTypescript','siVite']){o[k]={path:s[k].path,hex:s[k].hex}};console.log(JSON.stringify(o))"],
    cwd=NM.parent))


def brand(key):
    s = SI[key]
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#{s["hex"]}" d="{s["path"]}"/></svg>'


def img(svg, x, y, size):
    data = base64.b64encode(svg.encode()).decode()
    return f'<image href="data:image/svg+xml;base64,{data}" x="{x}" y="{y}" width="{size}" height="{size}"/>'


parts = []
add = parts.append


def text(x, y, s, size=14, weight=400, fill="#232F3E", anchor="middle"):
    add(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}">{s}</text>')


def tile(cx, y, icon_svg, title, sub=(), size=52):
    """가운데 정렬 타일: 아이콘, 굵은 이름, 회색 설명"""
    add(img(icon_svg, cx - size / 2, y, size))
    text(cx, y + size + 20, title, 14, 700)
    for i, line in enumerate(sub):
        text(cx, y + size + 38 + i * 16, line, 12, 400, "#545B64")


def group(x, y, w, h, label, color, icon=None, dash=None, fill="none"):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="{color}" stroke-width="1.5"{d}/>')
    if icon:
        add(img(icon, x, y, 28))
        text(x + 36, y + 19, label, 13, 700, color, "start")
    else:
        text(x + 10, y + 19, label, 13, 700, color, "start")


def arrow(points, color="#232F3E", dash=None):
    d = " ".join(f"{'M' if i == 0 else 'L'}{px},{py}" for i, (px, py) in enumerate(points))
    da = f' stroke-dasharray="{dash}"' if dash else ""
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="1.8"{da} marker-end="url(#arrow)"/>')


def badge(x, y, n):
    add(f'<circle cx="{x}" cy="{y}" r="12" fill="#232F3E"/>')
    text(x, y + 4.5, str(n), 13, 700, "#FFFFFF")


def label(x, y, s, anchor="middle"):
    w = len(s) * 7.2 + 10
    bx = x - w / 2 if anchor == "middle" else x
    add(f'<rect x="{bx}" y="{y - 12}" width="{w}" height="17" fill="#FFFFFF"/>')
    text(x if anchor == "middle" else x + 5, y + 1, s, 11.5, 500, "#545B64", anchor)


W, H = 1800, 900

# ---------- 바깥: 사용자, 외부 서비스 ----------
tile(130, 380, aws("User", "resource"), "사용자", ["원장 · 강사 · 학생", "휴대폰 · PC 브라우저"])
tile(1700, 175, brand("siGoogle"), "Google OAuth", ["OIDC · ID 토큰"], 44)
tile(1700, 440, aws("Users", "resource"), "운영자", ["SSH 없이 접속"], 48)
tile(1700, 740, brand("siGithubactions"), "GitHub Actions", ["테스트 → 이미지 → 배포"], 44)

# ---------- AWS ----------
group(260, 110, 1340, 770, "AWS Cloud", "#232F3E", aws("AWSCloudlogo", "architecture-group"))
tile(350, 150, aws("AmazonRoute53"), "Route 53", ["도메인"], 48)
group(450, 160, 1120, 700, "서울 리전 ap-northeast-2", "#00A4A6", aws("Region", "architecture-group"), "6 4")
group(480, 210, 760, 620, "VPC", "#8C4FFF", aws("VirtualprivatecloudVPC", "architecture-group"))
group(505, 255, 710, 340, "퍼블릭 서브넷", "#7AA116", aws("Publicsubnet", "architecture-group"), fill="#F2F8E6")
group(505, 620, 710, 185, "프라이빗 서브넷", "#00A4A6", aws("Privatesubnet", "architecture-group"), fill="#E6F6F7")

# EC2 + Docker Compose
add('<rect x="530" y="295" width="660" height="280" fill="#FFFFFF" stroke="#ED7100" stroke-width="1.5"/>')
add(img(aws("AmazonEC2"), 530, 295, 32))
add(img(brand("siDocker"), 572, 300, 22))
text(602, 316, "EC2 t4g.small · Docker Compose · 블루그린", 13, 700, "#ED7100", "start")

# 프론트엔드 컨테이너
add('<rect x="555" y="345" width="235" height="205" rx="6" fill="#F4FBFE" stroke="#61DAFB" stroke-width="1.5"/>')
text(672, 370, "프론트엔드", 14, 700)
add(img(brand("siNginx"), 585, 385, 40)); add(img(brand("siReact"), 652, 385, 40)); add(img(brand("siTypescript"), 719, 389, 32))
for i, line in enumerate(["Nginx: 화면 파일 · /api 프록시", "React 19 · TypeScript · Vite", "앱처럼 쓰는 웹 (휴대폰 우선)"]):
    text(672, 452 + i * 19, line, 12, 400, "#545B64")

# 백엔드 컨테이너
add('<rect x="830" y="345" width="335" height="205" rx="6" fill="#F3FAF0" stroke="#6DB33F" stroke-width="1.5"/>')
text(997, 370, "백엔드 · Spring Boot 4.1", 14, 700)
add(img(brand("siSpringboot"), 905, 385, 40)); add(img(brand("siOpenjdk"), 970, 385, 40)); add(img(aws("AWSIdentityandAccessManagement"), 1040, 389, 34))
for i, line in enumerate(["Java 25 · Spring Security (JWT · OIDC)", "모듈: 기관 · 연습실 · 학원 · 수납 · 사이트", "JPA · JdbcClient · Flyway V1~V13"]):
    text(997, 452 + i * 19, line, 12, 400, "#545B64")
text(997, 527, "모듈 의존 방향은 ArchUnit 테스트로 검사", 11, 400, "#879196")

# RDS
tile(997, 655, aws("AmazonRDS"), "RDS PostgreSQL 17", ["db.t4g.micro · 자동 백업", "보안 그룹: 5432는 EC2에서만"], 52)
add(img(brand("siPostgresql"), 1040, 660, 22))

# 리전 안, VPC 밖
tile(1405, 300, aws("AmazonSimpleStorageService"), "S3", ["로고 파일", "EC2 IAM 역할로만"], 48)
tile(1405, 470, aws("AWSSystemsManager"), "SSM Session Manager", ["SSH 포트 없음"], 44)
tile(1405, 640, aws("AWSBudgets"), "AWS Budgets", ["예산 알림 · 월 약 $37"], 44)

# Terraform 표시
add(img(brand("siTerraform"), 1340, 118, 20))
text(1366, 133, "VPC · EC2 · RDS · S3는 Terraform 코드로", 12, 600, "#844FBA", "start")

# ---------- 흐름 ----------
arrow([(130, 380), (130, 174), (318, 174)])                     # 1 도메인 조회
badge(130, 300, 1); label(200, 168, "도메인 조회")
arrow([(170, 418), (553, 418)])                                 # 2 HTTPS
badge(230, 418, 2); label(330, 410, "HTTPS :443")
arrow([(790, 448), (828, 448)])                                 # 3 /api 프록시
badge(809, 425, 3); label(809, 476, "/api/*")
badge(1150, 370, 4)                                             # 4 보안 검사 (백엔드 안)
arrow([(997, 550), (997, 653)])                                 # 5 SQL
badge(997, 600, 5); label(1062, 604, "JDBC :5432")
arrow([(1165, 400), (1300, 400), (1300, 324), (1378, 324)])                  # 6 S3
badge(1232, 400, 6); label(1232, 386, "PutObject · GetObject")
arrow([(1080, 345), (1080, 197), (1672, 197)], "#DD344C", "5 4")  # 7 구글
badge(1500, 197, 7); label(1300, 191, "코드 교환 · ID 토큰 검증")
arrow([(1676, 784), (1225, 784), (1225, 560), (1192, 560)], "#2088FF")  # 8 배포
badge(1520, 784, 8); label(1400, 777, "이미지 빌드 · 블루그린 배포")
arrow([(1676, 487), (1430, 487)], "#879196")                    # 9 운영자 → SSM
badge(1560, 487, 9)
arrow([(1380, 492), (1192, 492)], "#879196", "5 4")             # SSM → EC2
label(1290, 484, "세션")

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="'Pretendard Variable', Pretendard, -apple-system, 'Apple SD Gothic Neo', sans-serif">
<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>
<rect width="{W}" height="{H}" fill="#FFFFFF"/>
<text x="40" y="58" font-size="28" font-weight="800" fill="#232F3E">마디 아키텍처</text>
<text x="40" y="88" font-size="15" fill="#545B64">AWS 배포 계획 (ADR 0008) · 번호는 요청이 지나가는 순서</text>
{chr(10).join(parts)}
</svg>'''

steps = [
    "사용자가 도메인을 Route 53에서 찾는다.",
    "HTTPS로 EC2의 Nginx에 들어온다. 화면 파일(React)은 Nginx가 바로 준다.",
    "<code>/api/*</code> 요청만 Spring Boot로 넘긴다.",
    "Spring Security가 JWT를 확인하고, 그 기관의 ACTIVE 멤버인지 · 모듈이 켜져 있는지 · 역할이 맞는지 본다.",
    "업무 데이터는 프라이빗 서브넷의 RDS. 예약 겹침은 EXCLUDE 제약, 금액은 CHECK로 DB가 지킨다.",
    "로고 같은 파일은 S3. 키 대신 EC2의 IAM 역할로 접근하고, DB에는 파일 키만 둔다.",
    "구글 로그인: 인가 코드를 교환하고 ID 토큰을 검증한다(트랜잭션 밖, 타임아웃 5초).",
    "GitHub Actions가 테스트 → 이미지 빌드 → 블루그린 배포를 한다.",
    "운영자는 SSH 대신 SSM Session Manager로 들어간다. 예산은 AWS Budgets로 알린다.",
]
step_html = "".join(f"<li><b>{i + 1}</b><span>{s}</span></li>" for i, s in enumerate(steps))

OUT.write_text(f'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>마디 아키텍처</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css">
<style>
body {{ margin: 0; background: #F2F4F6; font-family: 'Pretendard Variable', Pretendard, -apple-system, sans-serif; color: #232F3E; }}
main {{ max-width: {W}px; margin: 24px auto; background: #fff; padding-bottom: 28px; }}
svg {{ display: block; width: 100%; height: auto; }}
ol {{ list-style: none; margin: 0; padding: 0 40px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px 28px; }}
li {{ display: flex; gap: 10px; font-size: 14px; line-height: 1.5; word-break: keep-all; }}
li b {{ flex: none; width: 24px; height: 24px; border-radius: 50%; background: #232F3E; color: #fff; display: grid; place-items: center; font-size: 13px; }}
code {{ font-size: 13px; }}
footer {{ padding: 18px 40px 0; font-size: 12px; color: #879196; }}
</style></head>
<body><main>
{svg}
<ol>{step_html}</ol>
<footer>아이콘: AWS Architecture Icons, Simple Icons. 지금은 로컬 Docker Compose로 같은 구성(EC2 대신 내 컴퓨터, RDS 대신 PostgreSQL 컨테이너, S3 대신 SeaweedFS)을 띄운다.</footer>
</main></body></html>
''')
print("wrote", OUT)
