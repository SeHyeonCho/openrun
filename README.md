# 🎟️ OpenRun

> 트래픽이 한 점으로 몰려도 좌석을 **초과 판매하지 않는** 선착순 예매 동시성 엔진.

콘서트 티켓팅, 한정판 드랍, 인기 팝업 예약처럼 "한정 수량 + 동시 폭주"가 본질인 상황에서, 초과 판매·서버 다운 없이 공정하게 처리하는 것을 목표로 합니다.

## 🧩 핵심 문제

- 오픈 순간 트래픽이 폭주한다
- 좌석은 한정 자원 — 초과 판매(oversell)는 **절대 0**이어야 한다
- 느린 결제(외부 I/O)가 트랜잭션 한가운데 낀다
- 서버가 여러 대인 분산 환경

## 🛠 기술 스택

- Java 21, Spring Boot 4
- MySQL 8, Redis (Redisson)
- Gradle, Docker Compose

## 🚀 로컬 실행

```bash
# 1. 환경 변수 파일 준비 (필수)
cp .env.example .env
# 필요하면 .env 안의 값(비밀번호·포트)을 수정

# 2. 인프라(MySQL · Redis) 띄우기
docker compose up -d

# 3. IDE에서 애플리케이션 실행
```
- **`.env`는 필수입니다.** 없으면 앱이 시작 단계에서 실패합니다.
- **실행 프로필 지정**: IDE 실행 구성의 **Active profiles**에 `local`을 지정해야 합니다. (또는 VM 옵션 `-Dspring.profiles.active=local`)
- DB·Redis는 Docker로 띄우고, 애플리케이션은 IDE에서 직접 실행합니다.
- 종료: `docker compose down`

## 🗺 로드맵

- [ ] P0 · 🎟️ 기본 예약 플로우
- [ ] P1 · 🧪 전략 비교 + 부하 테스트
- [ ] P2 · ⏳ 가상 대기열
- [ ] P3 · 💳 결제 연동
- [ ] P4 · 📡 API + 모니터링

> 진행 상황은 [Projects 보드](../../projects)와 [Milestones](../../milestones)에서 확인할 수 있습니다.

---

🚧 개발 진행 중인 개인 프로젝트입니다. 문서는 작업하면서 계속 채워나갑니다.
