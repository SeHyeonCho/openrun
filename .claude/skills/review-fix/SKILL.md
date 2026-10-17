---
name: review-fix
description: 현재 브랜치 PR에서 해결되지 않은 CodeRabbit 리뷰를 가져와 항목별로 반영 여부를 판단하고, 승인받은 항목만 컨벤션에 맞는 커밋으로 반영한다.
disable-model-invocation: true
allowed-tools: Bash(gh pr view *) Bash(gh api graphql *) Bash(git status *) Bash(git diff *) Bash(git log *) Bash(./gradlew *)
---

# CodeRabbit 리뷰 반영

## 현재 PR

!`gh pr view --json number,title,url --jq '"#\(.number) \(.title)\n\(.url)"' || echo "현재 브랜치에 열린 PR이 없다"`

## 절차

1. 해결되지 않은 리뷰 스레드를 가져온다. `<번호>`는 위 PR 번호다.
   ```
   gh api graphql -F owner='{owner}' -F name='{repo}' -F number=<번호> -f query='
     query($owner: String!, $name: String!, $number: Int!) {
       repository(owner: $owner, name: $name) {
         pullRequest(number: $number) {
           reviewThreads(first: 100) {
             nodes {
               isResolved
               isOutdated
               path
               line
               comments(first: 20) { nodes { databaseId author { login } body } }
             }
           }
         }
       }
     }'
   ```
   - `isResolved`가 false이고, 첫 댓글 작성자 login이 `coderabbitai`로 시작하는 스레드만 본다
   - CodeRabbit은 diff 범위 밖 지적과 사소한 지적(Nitpick)을 리뷰 본문에 접어서 넣는다. 최신 리뷰 본문도 확인한다:
     `gh api 'repos/{owner}/{repo}/pulls/<번호>/reviews?per_page=100' --jq '[.[] | select(.user.login | startswith("coderabbitai"))] | last | .body'`
2. 항목마다 반영, 보류, 반박 중 하나로 판단한다.
   - 동시성·정합성 지적을 가장 먼저 본다. 재현 테스트로 지적이 맞는지 확인할 수 있으면 확인한 뒤 판단한다
   - CLAUDE.md 원칙과 충돌하는 제안은 반영하지 않는다. 예: 외부 I/O를 트랜잭션 안으로 넣는 제안, 동시성 테스트 없이 락 범위를 바꾸는 제안, 엔티티에 `@Setter`를 추가하는 제안
   - 이슈의 마일스톤 범위를 넘는 개선 제안은 보류하고 별도 이슈 후보로 적는다
   - outdated 스레드는 현재 코드에도 해당하는지 확인한다
3. 판단 결과를 표로 보여준다: 위치 | 지적 요약 | 판단 | 이유. 승인 전에는 코드를 고치지 않는다.
4. 승인된 항목을 반영한다. 커밋은 변경 성격별로 나누고 CLAUDE.md 커밋 규칙을 따른다 (예: `fix: 홀드 만료 시각 비교 경계값 수정`). "리뷰 반영"처럼 무엇을 바꿨는지 드러나지 않는 메시지는 쓰지 않는다.
5. `SPRING_PROFILES_ACTIVE=local ./gradlew build`로 확인한다. 실패하면 push하지 않고 보고한다.
6. 승인을 받고 push한다. CodeRabbit이 새 커밋을 다시 리뷰한다.
7. 반박하거나 보류한 스레드에 이유를 답글로 남길지 묻는다. 승인되면 답글을 단다:
   `gh api -X POST 'repos/{owner}/{repo}/pulls/<번호>/comments/<databaseId>/replies' -f body='<이유>'`
   - 보류 항목은 `/new-issue`로 이슈를 만들자고 제안한다
8. 반영하지 않은 주요 지적과 그 이유를 PR 본문의 `💭 결정 · 고민 사항`에 추가할지 묻는다.
