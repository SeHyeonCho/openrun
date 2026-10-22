---
name: open-pr
description: 현재 이슈 브랜치의 커밋과 빌드를 점검하고, PR 템플릿을 채워 이슈와 같은 라벨·마일스톤으로 PR을 연다.
disable-model-invocation: true
allowed-tools: Bash(git status *) Bash(git log *) Bash(git diff *) Bash(gh issue view *) Bash(./gradlew *)
---

# PR 열기

## 현재 브랜치

!`git branch --show-current`

## main 이후 커밋

!`git log --oneline main..HEAD`

## 작업 트리

!`git status --short`

## 절차

1. 브랜치 이름이 `<type>/<번호>-<요약>` 형식인지 확인하고, 번호로 이슈를 조회한다: `gh issue view <번호>`. 형식이 다르면 멈춘다.
2. 커밋되지 않은 변경이 있으면 멈추고 알린다.
3. 커밋 목록을 점검한다. 커밋 형식이 틀렸거나 이슈와 무관한 변경이 섞여 있으면 PR 전에 알린다. 히스토리 재작성은 승인을 받은 뒤에만 한다.
4. 빌드와 테스트를 이전 결과를 재사용하지 않고 실행한다: `SPRING_PROFILES_ACTIVE=local ./gradlew build --rerun-tasks`
   - MySQL·Redis가 꺼져 있어 실패하면 `docker compose up -d`를 먼저 하자고 제안한다
   - 그 외 이유로 실패하면 PR을 열지 않고 원인을 보고한다
5. `.github/pull_request_template.md`를 읽고 본문을 채운다.
   - `📌 관련 이슈`: `Closes #<번호>`
   - `✨ 변경 사항`: `git diff main...HEAD`를 기준으로 무엇이 바뀌었는지 요약한다. 커밋 목록을 그대로 옮기지 않는다
   - `🧪 테스트 증명`: 4번에서 실제로 실행한 명령과 결과만 적는다. 출력에서 고르거나 다듬은 부분은 밝힌다. CI 실행 링크는 PR을 연 뒤 `/review-fix`에서 덧붙인다
   - `⚡ 성능 · 동시성`: 락·트랜잭션·공유 자원 변경이 없으면 섹션을 삭제한다. 있으면 실제로 확인한 항목만 체크하고, 측정하지 않은 수치 칸은 비워 둔다
   - `🏁 완성도`: CLAUDE.md의 마일스톤 완료 기준 중 이 PR이 실제로 채운 항목만 남긴다. 실패 시나리오는 어떤 경우를 검증했는지 구체적으로 적는다
   - `💭 결정 · 고민 사항`: 선택한 방식, 이유, 버린 대안
   - `✅ 체크리스트`: 실제로 확인한 항목만 체크한다
   - HTML 주석은 지운다
6. PR 제목은 이슈 제목을 그대로 쓰고, 라벨과 마일스톤은 이슈에서 복사한다. 담당자는 본인으로 지정한다.
7. 초안(제목, 라벨, 마일스톤, 본문)을 보여주고 승인을 받은 뒤 진행한다.
   `git push -u origin <브랜치>`
   `gh pr create --base main --title "<제목>" --body-file <임시 파일> --label "<라벨>" --milestone "<마일스톤 제목>" --assignee @me`
8. PR URL을 알려주고, CI와 CodeRabbit 리뷰가 끝나면 `/review-fix`로 처리하라고 안내한다. 머지는 사용자가 요청하면 CLAUDE.md의 머지 절차대로 한다.
