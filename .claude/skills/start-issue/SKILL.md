---
name: start-issue
description: GitHub 이슈 번호를 받아 컨벤션에 맞는 브랜치를 최신 main에서 만들고, 이슈 완료 조건 기준으로 구현 계획을 세운다.
argument-hint: <이슈 번호> [추가 요청]
arguments: [issue]
disable-model-invocation: true
allowed-tools: Bash(gh issue view *) Bash(git status *) Bash(git fetch *) Bash(git switch *) Bash(git pull --ff-only *)
---

# 이슈 #$issue 작업 시작

요청: $ARGUMENTS

## 이슈

!`gh issue view $issue`

## 작업 트리

!`git status --short --branch`

## 절차

1. 이슈가 닫혀 있거나 라벨·마일스톤이 없으면 멈추고 알린다.
2. 커밋되지 않은 변경이 있으면 멈추고 어떻게 할지 묻는다.
3. 브랜치 이름을 정한다: `<type>/$issue-<영문 kebab-case 2~5단어>`. type은 이슈 제목의 type을 그대로 쓴다.
4. 최신 main에서 분기한다.
   `git switch main && git pull --ff-only && git switch -c <브랜치>`
5. 이슈의 요구사항과 완료 조건을 기준으로 구현 계획을 세운다.
   - 바꿀 파일과 커밋 단위를 순서대로 나열한다. 커밋 메시지 초안도 함께 적는다
   - 이슈 마일스톤의 범위를 넘는 작업은 계획에 넣지 않고 "범위 밖"으로 따로 표시한다
   - 좌석 상태, 락, 트랜잭션에 영향을 주면 어떤 동시성 테스트로 검증할지 적는다
   - 요청에 이슈 번호 외의 말이 있으면 그 요청도 계획에 넣을지 함께 묻는다
6. 계획을 보여주고 승인을 받은 뒤 구현을 시작한다. 커밋은 계획한 단위대로 나눈다.
