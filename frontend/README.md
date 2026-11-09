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
- 실제 서버에 붙이려면 `frontend/.env.development.local`에 `VITE_API_MOCK=false`를 넣는다. `/api` 요청은 개발 서버가 `http://localhost:8080`으로 넘긴다
- 응답 타입은 `src/api/types.ts` 한 파일에 모았다. API 계약(#31)이 나오면 이 파일과 `src/api/queries.ts`의 경로를 맞춘다
- 카카오 로그인은 아직 서버에 없어서 `src/auth/useLogin.ts`가 목 로그인을 부른다

## 구조

| 경로 | 내용 |
|---|---|
| `src/pages/` | 공연 페이지, 수량 선택, 주문, 내 주문 |
| `src/components/` | 버튼, 배지, 티켓 카드, 하단 바, 토스트 같은 공통 컴포넌트 |
| `src/api/` | 응답 타입, 요청과 실패 응답(Problem Details) 처리, React Query 훅 |
| `src/lib/` | 서버 시각 보정, 날짜 표시, 판매 상태 판단, 공유, 캘린더 파일 |
| `src/mocks/` | MSW 핸들러와 목 데이터, 시나리오 |
| `src/styles/tokens.css` | 디자인 시스템 토큰 |
| `src/copy.ts` | 화면 문구. 어휘를 바꿀 때는 이 파일만 고친다 |
