# 좌석 예매 API 명세

## 1. 범위

기본 예약 흐름(P0)에서 구매자 화면이 쓰는 API다. [기능 명세](booking-flow.md) 8절의 화면 구조와 "화면마다 필요한 데이터" 표를 기준으로 한다.

- 아직 구현하지 않은 목표 계약이다. 지금 구현된 API와 다른 점은 10절에 적었다
- 다루지 않는 것: 지정석 좌석 배치(P3), 주최자 화면(P2·P3), 결제(P2), 입장 확인(P3)

## 2. 공통 규칙

### 경로와 본문

- 모든 API는 `/api` 아래에 둔다
- 요청과 응답 본문은 UTF-8 JSON이다. 실패 응답은 `application/problem+json`이다

### 시각

- 시각은 ISO 8601 UTC 문자열이다. 끝에 `Z`를 붙이고, 소수 초는 최대 6자리다. 예: `2026-12-05T10:00:00Z`, `2026-12-05T10:00:00.123456Z`
- 판매 시작·마감, 선점 기한 같은 판단은 서버 시각으로 한다

### 서버 현재 시각

- JSON 본문이 있는 모든 응답에 `serverTime`을 넣는다. 성공 응답과 실패 응답 모두다
- 화면은 요청을 보낸 시각과 응답을 받은 시각의 중간을 `serverTime`으로 보고 기기 시계와의 차이를 구한다. 카운트다운과 남은 시간은 이 차이를 보정해 보여 준다
- 화면의 시간은 보여 주기만 한다. 할 수 있는지는 서버가 판단하고, 화면의 시간이 남아 있어도 서버가 거부하면 거부 이유를 보여 준다

### 실패 응답

실패 응답은 RFC 9457 Problem Details 형식이다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | string | 지금은 항상 `about:blank` |
| `title` | string | HTTP 상태 이름 |
| `status` | number | HTTP 상태 |
| `detail` | string | 사람이 읽는 설명. 화면은 이 문장을 그대로 보여 주지 않고 `code`로 안내를 고른다 |
| `code` | string | 규칙 위반의 종류. 규칙 위반이 아니면 없다 |
| `seatIds` | number[] | 문제가 된 지정석. 해당할 때만 있다 |
| `serverTime` | string | 서버 현재 시각 |

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "판매 시작 전입니다.",
  "code": "SALES_NOT_OPEN",
  "serverTime": "2026-11-20T10:59:59.412000Z"
}
```

모든 API에 공통으로 생길 수 있는 실패는 아래와 같다. API별 절에는 그 API에만 있는 실패를 적는다.

| 상황 | HTTP 상태 | `code` |
|---|---|---|
| 필수 값이 없거나 형식이 틀림 | 400 | 없음 |
| 로그인이 필요한 API를 로그인하지 않고 부름 | 401 | 없음 |
| 상태를 바꾸는 요청에 CSRF 토큰이 없거나 틀림 | 403 | 없음 |
| 없는 자원, 다른 구매자의 주문을 확정·취소 | 404 | 없음 |
| 서버 오류 | 5xx | 없음 |

### 로그인

- 구매자는 카카오 계정으로 로그인한다. 따로 가입하는 절차는 없다
- 화면은 로그인할 때 `/oauth2/authorization/kakao`로 이동한다. 로그인이 끝나면 서버는 화면으로 돌려보내고, 화면은 이동하기 전에 저장해 둔 경로로 돌아간다
- 로그인 상태는 서버 세션 쿠키로 유지한다. 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax`다. 세션 유지 기간은 카카오 로그인 구현 이슈에서 정한다
- 서버를 여러 대로 늘리면(P6) 세션을 Redis로 옮긴다. API는 바뀌지 않는다

### CSRF

- 상태를 바꾸는 요청(`POST`)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 담아 보낸다. 서버가 쿠키를 내려주므로 화면이 토큰을 따로 받을 API는 없다
- 토큰이 없거나 틀리면 `403`이다. 요청은 처리되지 않는다

### 공통 값

| 이름 | 값 | 설명 |
|---|---|---|
| 판매 상태 | `BEFORE_SALE`, `ON_SALE`, `SOLD_OUT`, `SALES_CLOSED` | `serverTime` 기준으로 계산한다 |
| 좌석 종류 | `FREE`(자유석), `RESERVED`(지정석) | 한 회차는 한 종류만 판다 |
| 주문 상태 | `HELD`, `CONFIRMED`, `EXPIRED`, `CANCELLED` | `serverTime` 기준이다. 기한이 지난 `HELD` 주문은 `EXPIRED`로 준다 |

## 3. API 목록

| API | 로그인 | 쓰는 화면 |
|---|---|---|
| `GET /api/me` | 필요 | 모든 화면 |
| `POST /api/logout` | 필요 없음 | 모든 화면 |
| `GET /api/performances/{performanceId}` | 필요 없음 | 공연 페이지, 수량 선택 |
| `GET /api/showtimes/{showtimeId}/availability` | 필요 | 수량 선택 |
| `POST /api/orders` | 필요 | 수량 선택 |
| `GET /api/orders/{token}` | 필요 없음(주문 토큰) | 주문 |
| `POST /api/orders/{token}/confirm` | 필요(주문을 만든 구매자) | 주문 |
| `POST /api/orders/{token}/cancel` | 필요(주문을 만든 구매자) | 주문 |
| `GET /api/me/orders` | 필요 | 내 주문 |
