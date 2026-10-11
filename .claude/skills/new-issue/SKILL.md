---
name: new-issue
description: OpenRun 컨벤션(type, 라벨, 마일스톤, 이슈 템플릿)에 맞춰 GitHub 이슈 초안을 쓰고 승인 후 등록한다.
argument-hint: <만들 작업 설명>
disable-model-invocation: true
allowed-tools: Bash(gh api *) Bash(gh label list *) Bash(gh issue list *)
---

# 새 이슈 작성

요청: $ARGUMENTS

## 현재 마일스톤

!`gh api 'repos/{owner}/{repo}/milestones' --jq '.[] | "- \(.title) (열린 이슈 \(.open_issues)개) \(.description // "")"' || echo "마일스톤 조회 실패: gh auth status를 확인한다"`

## 열린 이슈 (중복 확인용)

!`gh issue list --state open --limit 30 || true`

## 절차

1. 열린 이슈 중 같은 작업이 있으면 멈추고 알린다.
2. CLAUDE.md의 type 표에서 type 하나를 고른다. 두 가지 성격이 섞여 있으면 이슈를 나누자고 제안한다.
3. 위 마일스톤 중 하나를 고른다. 어느 단계인지 모호하면 후보와 이유를 제시하고 묻는다.
4. 템플릿을 읽고 본문을 채운다.
   - `feat`: `.github/ISSUE_TEMPLATE/` 아래의 feature-request 템플릿
   - `fix`: 같은 위치의 bug-report 템플릿
   - `refactor`, `test`, `docs`, `chore`: feature-request 구조를 쓰되 `💡 기능 설명`을 `💡 작업 설명`으로 바꾼다
   - 템플릿의 front matter와 HTML 주석은 본문에 넣지 않는다
   - `📋 상세 내용 & 요구사항`은 완료 여부를 판단할 수 있는 체크리스트로 쓴다
   - 좌석 상태를 바꾸는 작업이면 완료 조건에 `동시성 테스트로 초과 판매 0건 확인`을 넣는다
   - `🗂 로드맵 단계`에는 고른 마일스톤 제목을 적는다
5. 초안을 보여준다: 제목(`<type>: <한국어 요약>`), 라벨, 마일스톤, 본문. 승인 전에는 등록하지 않는다.
6. 승인되면 본문을 임시 파일에 쓰고 등록한다.
   `gh issue create --title "<제목>" --label "<라벨>" --milestone "<마일스톤 제목>" --body-file <임시 파일>`
7. 이슈 번호와 URL을 알려주고, 바로 시작하려면 `/start-issue <번호>`를 쓰라고 안내한다.
