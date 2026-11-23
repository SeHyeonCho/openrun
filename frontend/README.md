# OpenRun 구매자 화면

휴대폰에서 링크로 여는 구매자 예매 화면이다. 화면 구조와 동작은 [기능 명세 8절](../docs/spec/booking-flow.md)을 따른다.

## 실행

Node 22.22.2 이상(22.x), 24.15.0 이상(24.x) 또는 26 이상이 필요하다. 테스트 도구(jsdom)가 요구하는 범위다. CI는 Node 24로 돌린다.

```bash
cd frontend
npm ci
npm run dev      # http://localhost:5173, 목 API로 뜬다
npm test         # Vitest
npm run build    # 타입 검사 후 dist/ 에 빌드
```

## 목 API와 실제 서버

- `npm run dev`는 `.env.development`의 `VITE_API_MOCK=true`로 MSW를 띄운다. 화면 위쪽의 "목 데이터" 띠를 열면 시나리오, 로그인 상태, 화면 바로가기를 고를 수 있다
- 주소에 `?mock=<시나리오>`를 붙여도 시나리오를 고를 수 있다. 시나리오 목록은 `src/mocks/scenarios.ts`에 있다
- 실제 서버에 붙이려면 `VITE_API_MOCK=false npm run dev`로 띄우거나 `frontend/.env.development.local`에 `VITE_API_MOCK=false`를 넣는다. 서버는 로컬 프로필(`./gradlew bootRun --args='--spring.profiles.active=local'`)로 먼저 띄운다
- 개발 서버는 `/api`, `/oauth2`, `/login/oauth2` 요청을 `http://localhost:8080`으로 넘긴다. 카카오 앱의 Redirect URI에 `http://localhost:5173/login/oauth2/code/kakao`를 등록해야 개발 서버를 거쳐 로그인할 수 있다
- 로그인 버튼은 지금 화면의 경로를 `sessionStorage`에 저장하고 `/oauth2/authorization/kakao`로 이동한다. 로그인이 끝나 서버가 `/`로 돌려보내면 저장한 경로로 돌아간다. 목 API로 띄우면 이동하지 않고 목 로그인을 부른다
- 응답 타입은 `src/api/types.ts` 한 파일에 모았다. 경로와 필드는 [API 명세](../docs/spec/booking-api.md)를 따른다

## 구조

| 경로 | 내용 |
|---|---|
| `src/pages/` | 첫 화면(로그인 뒤 돌아가기), 공연 페이지, 수량 선택, 주문, 내 주문 |
| `src/auth/` | 카카오 로그인 이동, 로그인 뒤 돌아갈 경로, 로그아웃 |
| `src/components/` | 버튼, 배지, 티켓 카드, 하단 바, 토스트 같은 공통 컴포넌트 |
| `src/api/` | 응답 타입, 요청과 실패 응답(Problem Details) 처리, React Query 훅 |
| `src/lib/` | 서버 시각 보정, 날짜 표시, 판매 상태 판단, 공유, 캘린더 파일 |
| `src/mocks/` | MSW 핸들러와 목 데이터, 시나리오 |
| `src/styles/tokens.css` | 디자인 시스템 토큰 |
| `src/copy.ts` | 화면 문구. 어휘를 바꿀 때는 이 파일만 고친다 |
