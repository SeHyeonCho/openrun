# OpenRun

선착순 티켓 예매 동시성 엔진이다. 어떤 동시 요청에서도 좌석을 초과 판매(oversell)하지 않는 것이 핵심 불변식이다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle
- MySQL 8.4, Redis 7, Redisson 4.5.0 (Spring BOM이 관리하지 않으므로 버전을 직접 명시한다)
- 로컬 인프라(MySQL, Redis)는 Docker Compose로 띄우고, 애플리케이션은 IDE에서 실행한다

## 명령어

- 인프라 기동 / 종료: `docker compose up -d` / `docker compose down`
- 빌드: `SPRING_PROFILES_ACTIVE=local ./gradlew build` (테스트를 포함하므로 아래 테스트와 같은 조건이 필요하다)
- 테스트: `SPRING_PROFILES_ACTIVE=local ./gradlew test`
  - 현재 테스트는 실제 MySQL·Redis에 붙는다. `.env`가 있고 `docker compose up -d`가 된 상태여야 한다
  - Testcontainers로 전환하면 이 항목을 갱신한다
- 로컬 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`
- 훅 활성화 (클론 후 한 번): `git config core.hooksPath .githooks`

## GitHub Flow

모든 작업은 이슈에서 시작한다. 순서는 이슈 → 브랜치 → 커밋 → PR → CodeRabbit 리뷰 반영 → Squash Merge다.
반복 작업은 스킬로 처리한다: `/new-issue`, `/start-issue <번호>`, `/open-pr`, `/review-fix`.

### type과 라벨

type은 아래 여섯 개만 쓴다. 이슈 제목, 브랜치, 커밋, PR 제목, 라벨이 모두 같은 type을 공유한다.

| type | 라벨 | 용도 |
|---|---|---|
| `feat` | ✨ Feat | 새 기능 |
| `fix` | 🐛 Bug | 의도와 다르게 동작하는 문제 수정 |
| `refactor` | ♻️ Refactor | 동작 변화 없는 구조 개선 |
| `test` | ✅ Test | 테스트 추가·수정 |
| `docs` | 📝 Docs | 문서·주석 |
| `chore` | 🔧 Chore | 빌드·설정·의존성 |

### 이슈

- 제목: `<type>: <한국어 요약>` (예: `chore: 로컬 개발 환경 세팅`)
- 본문은 `.github/ISSUE_TEMPLATE/`의 템플릿 구조를 따른다
- 라벨 하나와 마일스톤 하나를 반드시 지정한다
- 담당자는 본인으로 지정한다 (`--assignee @me`)

### 브랜치

- `<type>/<이슈번호>-<영문-kebab-요약>` (예: `chore/1-setup-local-dev-env`)
- 항상 최신 `main`에서 분기한다
- `main`에 직접 커밋하거나 푸시하지 않는다. force push를 하지 않는다

### 커밋

- 형식: `<type>: <한국어 요약>`
- 요약은 명사형으로 끝내고 마침표를 찍지 않는다 (예: `chore: 환경변수를 .env로 분리`, `chore: 파일 끝 개행 문자 추가`)
- 커밋 하나에는 논리적 변경 하나만 담는다. 리뷰어가 커밋 단위로 따라갈 수 있게 나눈다
- 형식은 `.githooks/commit-msg`가 검사한다. 훅이 거부하면 메시지를 고쳐서 다시 커밋한다

### PR

- 제목은 이슈 제목과 같다. Squash Merge 후 이 제목이 `main`의 커밋 메시지가 된다
- 본문은 `.github/pull_request_template.md`를 채운다
  - `Closes #<이슈번호>`는 본문의 `📌 관련 이슈`에 쓴다
  - `🧪 테스트 증명`에는 실제로 실행한 명령과 결과를 적는다. 실행하지 않은 테스트를 통과했다고 쓰지 않는다
  - 락·트랜잭션·공유 자원과 무관한 변경이면 `⚡ 성능 · 동시성` 섹션을 삭제한다
  - `💭 결정 · 고민 사항`에는 선택한 방식, 이유, 버린 대안을 적는다
- 이슈와 같은 라벨과 마일스톤을 달고, 담당자는 본인으로 지정한다 (`--assignee @me`)

### 코드 리뷰 (CodeRabbit)

- PR을 열면 CodeRabbit이 리뷰한다. 리뷰는 `/review-fix`로 처리한다
- 리뷰 설정은 `.coderabbit.yaml`에 있다. CodeRabbit은 이 파일도 리뷰 기준으로 읽으므로, 컨벤션은 여기에만 적고 `.coderabbit.yaml`에는 여기에 없는 리뷰 지침만 둔다
- 지적을 그대로 받아들이지 않는다. 이 파일의 원칙에 비춰 반영, 보류, 반박 중 하나로 판단하고 이유를 남긴다
- 반영 커밋도 일반 커밋과 같은 규칙을 따른다. 무엇을 바꿨는지 드러나게 쓴다

## 마일스톤

- GitHub 마일스톤(P0~P7)이 로드맵의 진실의 원천이다. 각 단계의 범위와 릴리스 버전은 마일스톤 설명에 있다. 이 파일에 복사하지 않고 필요할 때 조회한다:
  `gh api 'repos/{owner}/{repo}/milestones' --jq '.[] | "\(.title)\n  \(.description)"'`
- 모든 이슈와 PR은 마일스톤 하나에 속한다
- 작업 중인 이슈의 마일스톤 범위를 넘는 구현을 하지 않는다. 다음 단계에 필요한 기능이나 인프라(메시지 브로커, 서비스 분리 등)를 미리 넣지 않고, 필요하면 별도 이슈로 제안한다
- P0~P5는 모듈러 모놀리스로 진행하고, 서비스 분리는 P6(대기열)과 P7(결제)에서 한다. 분리 전후를 같은 조건의 수치로 비교한다

### 마일스톤 완료 기준

마일스톤은 아래 다섯 가지를 모두 채워야 닫는다. 각 PR은 자신에게 해당하는 항목을 PR 본문의 `🏁 완성도` 섹션에 적는다.

1. 테스트: 동시성 테스트와 실제 MySQL·Redis를 띄운 통합 테스트
2. 측정: 같은 조건의 k6 부하 테스트로 적용 전후 수치 비교
3. 실패 시나리오: 정상 경로뿐 아니라 실패할 때의 동작(만료 경합, 타임아웃, Redis 장애 등)
4. 관측: 그 현상이 메트릭·대시보드에서 보인다
5. 기록: 선택한 방식, 이유, 버린 대안. 마일스톤을 대표하는 결정은 `docs/adr/NNNN-<제목>.md`로 남긴다

마일스톤을 닫을 때는 README의 수치와 설명을 그 시점 기준으로 갱신하고 마일스톤 설명의 버전으로 릴리스 태그를 만든다.

## 동시성 · 설계 원칙

- 초과 판매 방지는 처리량이나 응답 속도와 맞바꾸지 않는다. 좌석 상태를 바꾸는 코드에는 동시성 테스트가 함께 있어야 한다
- 좌석 선점은 `AVAILABLE → HELD` 상태 전이로 표현하고, 만료는 `held_until`로 판단한다. 락은 이 전이를 보호하는 수단이다
- 외부 I/O(결제 등)를 락 구간이나 DB 트랜잭션 안에 두지 않는다
- 도메인(queue, booking, payment) 간에 테이블을 조인하지 않는다. 다른 도메인은 인터페이스나 이벤트로만 호출한다. 이후 서비스 분리를 위한 경계다
- 성능 관련 변경은 수치(TPS, p99, 초과 판매 건수)로 전후를 비교한다. 측정 없이 "빨라졌다"고 쓰지 않는다

## 코드 컨벤션

- 들여쓰기는 공백 4칸, YAML은 2칸이다. `.editorconfig`를 따른다
- 패키지 루트는 `com.openrun.reservation`이다
- 엔티티
  - `BaseEntity`를 상속하고 생성·수정 시각은 JPA Auditing으로 채운다
  - `@Setter`, `@Data`를 쓰지 않는다. 상태 변경은 의도가 드러나는 도메인 메서드로 한다 (예: `seat.hold(userId, heldUntil)`)
  - 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 둔다
- 의존성 주입은 생성자 주입(`@RequiredArgsConstructor`)만 쓴다
- `@Transactional`은 서비스 계층에 두고, 조회 전용 메서드는 `readOnly = true`로 둔다
- `open-in-view: false`가 전제다. 지연 로딩은 트랜잭션 안에서 끝낸다
- 요청·응답 DTO는 `record`로 만든다
- 불필요한 주석, 주석 처리된 코드, 디버깅용 출력을 남기지 않는다

## 테스트

- 동시성 테스트는 `ExecutorService`와 `CountDownLatch`로 N개 스레드를 동시에 출발시키고, 성공 건수와 최종 좌석 상태를 함께 검증한다
- 테스트 메서드에는 한국어 `@DisplayName`으로 검증하는 행위를 적는다
- 버그를 고칠 때는 재현 테스트를 먼저 작성해 실패를 확인한 뒤 수정한다

## 설정 · 보안

- 프로필은 `application.yml`(공통), `application-local.yml`, `application-prod.yml`로 나뉜다. 베이스 설정에 `spring.profiles.active`를 넣지 않는다
- `.env`는 필수이며 커밋하지 않는다. 새 환경변수를 추가하면 `.env.example`에 키를 함께 추가한다
- 새 의존성을 추가할 때는 이유를 먼저 설명하고 확인을 받는다

## 작업 방식

- 응답은 한국어로 한다
- 문서와 PR 본문은 `~한다` 체로 쓰고 마케팅성 문구를 쓰지 않는다
- 여러 파일에 걸친 변경은 구현 전에 계획을 먼저 보여준다
