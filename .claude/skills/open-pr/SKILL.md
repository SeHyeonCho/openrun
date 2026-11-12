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
4. 빌드와 테스트를 이전 결과를 재사용하지 않고 실행한다: `./gradlew build --rerun-tasks`
   - Docker가 꺼져 있어 Testcontainers가 실패하면 Docker를 먼저 켜자고 제안한다
   - 그 외 이유로 실패하면 PR을 열지 않고 원인을 보고한다
5. 문서만 바꿨어도 앱이 뜨는지 확인한다.
   - 시작 전에 `docker compose ps --services --status running`으로 이미 켜져 있는 서비스를 기록한다
   - 이미 켜져 있는 서비스는 다른 세션이 쓰고 있을 수 있어 건드리지 않는다
   - 꺼져 있는 서비스는 서비스마다 `docker compose up -d --force-recreate <서비스>`로 지금 설정에 맞게 새로 만들어 띄운다. 꺼져 있던 컨테이너는 예전 설정을 들고 있을 수 있다. 모든 서비스가 healthy가 될 때까지 기다린다
   - 켜져 있던 서비스의 설정이 지금 설정과 달라 앱 확인이 실패하면, 그 서비스를 다시 만들지 말고 사용자에게 먼저 묻는다
   - `./gradlew bootRun --args='--spring.profiles.active=local'`을 백그라운드로 띄우고, 로그에서 마이그레이션 검증과 `Started` 줄을 확인한다
   - 데이터를 바꾸지 않는 요청만 보낸다. 예: 샘플 회차의 좌석 조회, 없는 회차·주문 요청의 `404`
   - 확인한 뒤 앱을 끄고, 이번 확인에서 새로 켠 서비스만 `docker compose stop <서비스>`로 끈다. 원래 켜져 있던 서비스는 다른 세션이 쓰고 있을 수 있어 그대로 둔다
6. `.github/pull_request_template.md`를 읽고 본문을 채운다.
   - `📌 관련 이슈`: `Closes #<번호>`
   - `✨ 변경 사항`: `git diff main...HEAD`를 기준으로 무엇이 바뀌었는지 요약한다. 커밋 목록을 그대로 옮기지 않는다
   - `🧪 테스트 증명`: 4·5번에서 실제로 실행한 명령과 결과만 적는다. 빌드 출력 블록 다음은 CLAUDE.md의 순서를 따른다(CI 링크 → 테스트 수와 이번에 추가한 것 → 테스트별 상세 → `bootRun` 같은 수동 확인 → 출력에서 다듬은 부분). 테스트 수는 `build/test-results/test/*.xml`에서 센다. CI 링크는 PR을 연 뒤 빌드가 끝나면 넣는다
   - `⚡ 성능 · 동시성`: 락·트랜잭션·공유 자원 변경이 없으면 섹션을 삭제한다. 있으면 실제로 확인한 항목만 체크하고, 측정하지 않은 수치 칸은 비워 둔다
   - `🏁 완성도`: CLAUDE.md의 마일스톤 완료 기준 중 이 PR이 실제로 채운 항목만 남긴다. 실패 시나리오는 어떤 경우를 검증했는지 구체적으로 적는다
   - `💭 결정 · 고민 사항`: 선택한 방식, 이유, 버린 대안
   - `✅ 체크리스트`: 실제로 확인한 항목만 체크한다
   - HTML 주석은 지운다
7. PR 제목은 이슈 제목을 그대로 쓰고, 라벨과 마일스톤은 이슈에서 복사한다. 담당자는 본인으로 지정한다.
8. 초안(제목, 라벨, 마일스톤, 본문)을 보여주고 승인을 받은 뒤 진행한다.
   `git push -u origin <브랜치>`
   `gh pr create --base main --title "<제목>" --body-file <임시 파일> --label "<라벨>" --milestone "<마일스톤 제목>" --assignee @me`
9. PR URL을 알려주고, CI와 CodeRabbit 리뷰가 끝나면 `/review-fix`로 처리하라고 안내한다. 머지는 사용자가 요청하면 CLAUDE.md의 머지 절차대로 한다.
