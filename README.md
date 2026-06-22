# 🎟️ OpenRun

> 동시 요청이 몰리는 선착순 예매에서 좌석 동시성을 다루는 엔진.

콘서트 티켓팅, 한정판 드랍처럼 한정된 수량에 동시 요청이 몰리는 상황에서, 좌석 정합성을 지키며 처리하는 것을 목표로 합니다.

## 🧩 핵심 문제

- 오픈 시점에 동시 요청이 몰린다
- 좌석은 한정 자원이라 초과 판매가 발생하면 안 된다
- 결제 같은 느린 외부 I/O가 처리 흐름 중간에 들어온다
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
- `.env`는 필수입니다. 없으면 앱이 시작 단계에서 실패합니다.
- 실행 프로필 지정: IDE 실행 구성의 Active profiles에 `local`을 지정해야 합니다. (또는 VM 옵션 `-Dspring.profiles.active=local`)
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
