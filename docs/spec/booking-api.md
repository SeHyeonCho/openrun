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

## 4. 로그인 상태

### `GET /api/me`

로그인한 구매자를 알려 준다.

| 응답 필드 | 타입 | 설명 |
|---|---|---|
| `buyerId` | string | 구매자를 구분하는 추측할 수 없는 값. 화면이 보관한 선점 요청의 주인을 확인할 때 쓴다(8절) |
| `nickname` | string | 카카오 닉네임. 예매자 이름의 처음 값이다 |
| `serverTime` | string | |

| 상황 | HTTP 상태 |
|---|---|
| 로그인함 | 200 |
| 로그인하지 않음 | 401 |

```http
GET /api/me
```

```json
{
  "buyerId": "b_7Qm2xK9pLr",
  "nickname": "밤산책",
  "serverTime": "2026-11-20T10:58:00Z"
}
```

### `POST /api/logout`

로그인 세션을 끝낸다. 성공하면 `204`이고 본문이 없다. 로그인하지 않은 상태에서 불러도 아무것도 바꾸지 않고 `204`다. CSRF 토큰은 필요하다.

```http
POST /api/logout
X-XSRF-TOKEN: 4b1c...
```

```http
HTTP/1.1 204 No Content
```

## 5. 공연

### `GET /api/performances/{performanceId}`

공연과 회차를 알려 준다. 로그인하지 않아도 볼 수 있고, 구매자마다 내용이 같다.

| 응답 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 공연 ID |
| `title` | string | 공연 제목 |
| `venue` | string | 장소 |
| `posterUrl` | string \| null | 포스터 이미지 주소 |
| `holdMinutes` | number | 선점 유지 시간(분). "N분 안에 확정" 안내에 쓴다 |
| `showtimes` | Showtime[] | 회차 목록. 공연 시작 시각 순이다 |
| `serverTime` | string | |

Showtime:

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 회차 ID |
| `startsAt` | string | 공연 시작 시각 |
| `endsAt` | string | 공연 종료 시각 |
| `salesOpensAt` | string | 판매 시작 시각 |
| `salesClosesAt` | string | 판매 마감 시각. 따로 정하지 않았으면 `endsAt`과 같다 |
| `seatKind` | string | `FREE` 또는 `RESERVED` |
| `salesStatus` | string | 판매 상태 |
| `remaining` | number | 남은 좌석 수. 확정 좌석과 기한 안의 선점 좌석을 뺀 수다 |
| `maxSeatsPerBuyer` | number | 1인 최대 좌석 수 |

| 상황 | HTTP 상태 |
|---|---|
| 성공 | 200 |
| 없는 공연 | 404 |

```http
GET /api/performances/1
```

```json
{
  "id": 1,
  "title": "NIGHT SIGNAL",
  "venue": "홍대 라이브홀 B2",
  "posterUrl": "https://cdn.example/posters/1.png",
  "holdMinutes": 10,
  "showtimes": [
    {
      "id": 12,
      "startsAt": "2026-12-05T10:00:00Z",
      "endsAt": "2026-12-05T12:30:00Z",
      "salesOpensAt": "2026-11-20T11:00:00Z",
      "salesClosesAt": "2026-12-05T12:30:00Z",
      "seatKind": "FREE",
      "salesStatus": "BEFORE_SALE",
      "remaining": 90,
      "maxSeatsPerBuyer": 4
    }
  ],
  "serverTime": "2026-11-20T10:58:00Z"
}
```

### `GET /api/showtimes/{showtimeId}/availability`

로그인한 구매자가 이 회차에서 더 고를 수 있는 수를 계산하는 데 필요한 값을 준다. 구매자마다 다른 값(`ownedCount`)이 있어서 공연 응답과 나눴다.

| 응답 필드 | 타입 | 설명 |
|---|---|---|
| `showtimeId` | number | |
| `salesStatus` | string | |
| `remaining` | number | |
| `maxSeatsPerBuyer` | number | |
| `ownedCount` | number | 이 구매자가 이 회차에서 가진 좌석 수. 확정 좌석과 기한 안의 선점 좌석이다 |
| `serverTime` | string | |

화면은 `min(remaining, maxSeatsPerBuyer - ownedCount)`까지 고르게 한다.

| 상황 | HTTP 상태 |
|---|---|
| 성공 | 200 |
| 없는 회차 | 404 |

```http
GET /api/showtimes/12/availability
```

```json
{
  "showtimeId": 12,
  "salesStatus": "ON_SALE",
  "remaining": 50,
  "maxSeatsPerBuyer": 4,
  "ownedCount": 2,
  "serverTime": "2026-11-20T11:00:03Z"
}
```

## 6. 주문

### 주문 응답

주문 API는 같은 모양의 주문을 돌려준다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `token` | string | 주문 토큰. 128비트 이상의 무작위 값을 URL에 쓸 수 있는 문자로 바꾼 것이다. 주문 링크와 QR에 담는다 |
| `status` | string | 주문 상태 |
| `performance` | object | `id`, `title`, `venue` |
| `showtime` | object | `id`, `startsAt`, `endsAt` |
| `quantity` | number | 좌석 수 |
| `entryNumbers` | number[] | 자유석의 입장번호. 오름차순이다. 지정석의 좌석 표시는 좌석 배치(P3)와 함께 정한다 |
| `buyerName` | string | 예매자 이름 |
| `heldUntil` | string | 선점 기한. 확정한 뒤에도 원래 기한을 그대로 준다 |
| `createdAt` | string | 선점한 시각 |
| `isOwner` | boolean | 지금 로그인한 구매자가 주문을 만든 사람이면 `true`. 로그인하지 않았으면 `false` |
| `serverTime` | string | |

```json
{
  "token": "o_Vx3k9QmT2pLw8RzA1nYc5d",
  "status": "HELD",
  "performance": { "id": 1, "title": "NIGHT SIGNAL", "venue": "홍대 라이브홀 B2" },
  "showtime": { "id": 12, "startsAt": "2026-12-05T10:00:00Z", "endsAt": "2026-12-05T12:30:00Z" },
  "quantity": 2,
  "entryNumbers": [37, 38],
  "buyerName": "밤산책",
  "heldUntil": "2026-11-20T11:10:03.218000Z",
  "createdAt": "2026-11-20T11:00:03.218000Z",
  "isOwner": true,
  "serverTime": "2026-11-20T11:00:03.240000Z"
}
```

### `POST /api/orders`

좌석을 선점하고 주문을 만든다. 로그인, CSRF 토큰, `Idempotency-Key` 헤더가 필요하다. 요청 식별자 규칙은 8절에 적었다.

| 요청 필드 | 타입 | 설명 |
|---|---|---|
| `showtimeId` | number | 회차 ID |
| `quantity` | number | 자유석 회차에서 고른 좌석 수. 1 이상의 정수다. 소수나 0 이하는 `400`이다 |
| `seatIds` | number[] | 지정석 회차에서 고른 좌석. 비어 있거나 같은 좌석이 두 번 있으면 안 된다 |
| `buyerName` | string | 예매자 이름. 앞뒤 공백을 빼고 1~20자다 |

- 자유석 회차는 `quantity`만, 지정석 회차는 `seatIds`만 보낸다. 둘 다 보내거나 둘 다 없으면 `400`이다
- 자유석은 남은 입장번호 가운데 가장 앞 번호부터 배정한다

```http
POST /api/orders
Content-Type: application/json
Idempotency-Key: 0b6a8f6e-3c1f-4e0b-9a52-7f2d8c41e9a3
X-XSRF-TOKEN: 4b1c...

{ "showtimeId": 12, "quantity": 2, "buyerName": "밤산책" }
```

처음 선점에 성공하면 `201 Created`와 함께 위 주문 응답 예시와 같은 `HELD` 주문을 돌려준다. 같은 식별자로 다시 보내면 같은 `201`과 그 주문의 지금 상태를 돌려준다. 그사이 확정·취소했거나 기한이 지났으면 `CONFIRMED`, `CANCELLED`, `EXPIRED`일 수 있다(8절).

| 상황 | HTTP 상태 | `code` |
|---|---|---|
| 선점함 | 201 | |
| 같은 식별자로 다시 보냄 | 처음 결과와 같다(8절) | |
| `Idempotency-Key`가 없거나 UUID 형식이 아님 | 400 | 없음 |
| 없는 회차 | 404 | 없음 |
| 판매 시작 전 | 409 | `SALES_NOT_OPEN` |
| 판매 마감 뒤 | 409 | `SALES_CLOSED` |
| 1인 최대 좌석 수 초과 | 409 | `SEAT_LIMIT_EXCEEDED` |
| 자유석 남은 수보다 많이 요청, 지정석이 선점 중·판매 완료·판매 제외 | 409 | `SEAT_NOT_AVAILABLE` (지정석이면 `seatIds`) |
| 회차에 없는 지정석 포함 | 409 | `SEAT_NOT_IN_SHOWTIME` (`seatIds`) |
| 같은 식별자를 다른 내용으로 보냄 | 422 | `IDEMPOTENCY_KEY_REUSED` |

### `GET /api/orders/{token}`

주문을 알려 준다. 주문 토큰을 가진 사람은 로그인하지 않아도 볼 수 있다. 함께 갈 사람에게 표를 보여 주거나 입구에서 QR을 띄우는 용도다.

```http
GET /api/orders/o_Vx3k9QmT2pLw8RzA1nYc5d
```

응답은 주문 응답 모양이다. 로그인하지 않은 사람이나 다른 구매자가 열면 `isOwner`가 `false`다. 아래는 링크를 받은 사람이 기한이 지난 주문을 열었을 때의 응답에서 달라지는 필드만 보인 것이다.

```json
{
  "token": "o_Vx3k9QmT2pLw8RzA1nYc5d",
  "status": "EXPIRED",
  "heldUntil": "2026-11-20T11:10:03.218000Z",
  "isOwner": false,
  "serverTime": "2026-11-20T11:15:00Z"
}
```

| 상황 | HTTP 상태 |
|---|---|
| 성공 | 200 |
| 없는 주문 | 404 |

### `POST /api/orders/{token}/confirm`

선점을 확정한다. 이번 단계에는 결제가 없어서 이 요청이 결제를 대신한다. 결제(P2)가 들어오면 결제 성공이 확정을 대신 부른다. 주문을 만든 구매자만 할 수 있고, CSRF 토큰이 필요하다. 요청 본문은 없다.

- 이미 확정한 주문을 다시 확정하면 같은 응답(`200`)을 돌려주고 아무것도 바꾸지 않는다. 그래서 결과를 모를 때 같은 주문으로 다시 보내도 된다

```http
POST /api/orders/o_Vx3k9QmT2pLw8RzA1nYc5d/confirm
X-XSRF-TOKEN: 4b1c...
```

성공 응답은 주문 응답 모양이다. 아래는 주문 응답 예시에서 달라지는 필드만 보인 것이다. 선점 기한은 확정한 뒤에도 원래 값 그대로다.

```json
{
  "token": "o_Vx3k9QmT2pLw8RzA1nYc5d",
  "status": "CONFIRMED",
  "heldUntil": "2026-11-20T11:10:03.218000Z",
  "isOwner": true,
  "serverTime": "2026-11-20T11:02:41.007000Z"
}
```

| 상황 | HTTP 상태 | `code` |
|---|---|---|
| 확정함, 이미 확정함 | 200 | |
| 없는 주문, 다른 구매자의 주문 | 404 | 없음 |
| 기한이 지났거나 만료된 주문 | 409 | `ORDER_EXPIRED` |
| 취소한 주문 | 409 | `ORDER_CANCELLED` |

### `POST /api/orders/{token}/cancel`

기한 전의 선점을 취소한다. 주문은 `CANCELLED`가 되고 좌석은 바로 다시 팔 수 있다. 주문을 만든 구매자만 할 수 있고, CSRF 토큰이 필요하다. 요청 본문은 없다.

- 이미 취소한 주문을 다시 취소하면 같은 응답(`200`)을 돌려주고 아무것도 바꾸지 않는다

```http
POST /api/orders/o_Vx3k9QmT2pLw8RzA1nYc5d/cancel
X-XSRF-TOKEN: 4b1c...
```

성공 응답은 주문 응답 모양이다. 아래는 주문 응답 예시에서 달라지는 필드만 보인 것이다.

```json
{
  "token": "o_Vx3k9QmT2pLw8RzA1nYc5d",
  "status": "CANCELLED",
  "isOwner": true,
  "serverTime": "2026-11-20T11:03:12.540000Z"
}
```

| 상황 | HTTP 상태 | `code` |
|---|---|---|
| 취소함, 이미 취소함 | 200 | |
| 없는 주문, 다른 구매자의 주문 | 404 | 없음 |
| 확정한 주문 | 409 | `ORDER_CONFIRMED` |
| 기한이 지났거나 만료된 주문 | 409 | `ORDER_EXPIRED` |

### `GET /api/me/orders`

로그인한 구매자의 주문 목록이다. 선점한 시각의 최근 순이다. 이번 단계는 페이지를 나누지 않는다.

| 응답 필드 | 타입 | 설명 |
|---|---|---|
| `orders` | object[] | 주문 응답에서 `buyerName`, `isOwner`, `serverTime`을 뺀 모양이다 |
| `serverTime` | string | |

```http
GET /api/me/orders
```

```json
{
  "orders": [
    {
      "token": "o_Vx3k9QmT2pLw8RzA1nYc5d",
      "status": "CONFIRMED",
      "performance": { "id": 1, "title": "NIGHT SIGNAL", "venue": "홍대 라이브홀 B2" },
      "showtime": { "id": 12, "startsAt": "2026-12-05T10:00:00Z", "endsAt": "2026-12-05T12:30:00Z" },
      "quantity": 2,
      "entryNumbers": [37, 38],
      "heldUntil": "2026-11-20T11:10:03.218000Z",
      "createdAt": "2026-11-20T11:00:03.218000Z"
    }
  ],
  "serverTime": "2026-11-20T11:02:00Z"
}
```

## 7. 실패 `code`와 화면 동작

화면이 보여 줄 안내와 다음 동작은 [기능 명세](booking-flow.md) 8절 "실패 상황별 화면" 표를 따른다.

| `code` | HTTP 상태 | API | 화면의 다음 동작 |
|---|---|---|---|
| `SALES_NOT_OPEN` | 409 | 선점 | 1.5초 간격으로 최대 5번 새 식별자로 자동 재시도 |
| `SALES_CLOSED` | 409 | 선점 | 공연 페이지로 |
| `SEAT_LIMIT_EXCEEDED` | 409 | 선점 | 이용 가능 수를 다시 받아 수량을 줄임 |
| `SEAT_NOT_AVAILABLE` | 409 | 선점 | 이용 가능 수를 다시 받아 수량을 줄임. 0이면 매진 안내 |
| `SEAT_NOT_IN_SHOWTIME` | 409 | 선점(지정석) | 좌석을 다시 고름 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 선점 | 화면이 식별자를 잘못 다시 쓴 것이다. 보관한 요청을 버리고 오류를 안내 |
| `ORDER_EXPIRED` | 409 | 확정, 취소 | 시간 만료 화면 |
| `ORDER_CANCELLED` | 409 | 확정 | 취소 화면 |
| `ORDER_CONFIRMED` | 409 | 취소 | 확정 화면 |

## 8. 선점 요청 식별자

결과를 모르는 선점 요청을 다시 보내거나 버튼을 여러 번 눌러도 주문이 하나만 생기게 하는 규칙이다. 판매 시작 순간처럼 응답이 늦어지기 쉬운 때에 특히 필요하다. 확정과 취소는 같은 주문으로 다시 보내면 같은 결과라서 식별자가 없다.

### 형식

- `Idempotency-Key` 헤더에 UUID 문자열(36자)을 담는다. 화면이 선점마다 새로 만든다
- 식별자는 구매자마다 따로 본다. 다른 구매자가 같은 식별자를 보내도 서로 관계없다

### 서버가 할 일

| 상황 | 서버 동작 |
|---|---|
| 처음 보는 식별자 | 처리한다. 결과가 선점 성공이거나 규칙 위반(`409`)이면 식별자, 요청 내용, 결과를 기록한다. 기록은 주문을 만드는 것과 같은 트랜잭션에서 해서, 주문만 생기고 기록이 없는 경우가 없게 한다 |
| 같은 구매자가 같은 식별자, 같은 내용으로 다시 보냄 | 기록한 결과를 돌려준다. 성공이었으면 `201`과 그 주문의 지금 상태를, 규칙 위반이었으면 같은 `409`와 `code`를 준다. `serverTime`은 새로 넣는다 |
| 같은 식별자를 다른 내용으로 보냄 | `422 IDEMPOTENCY_KEY_REUSED`로 거부하고 아무것도 바꾸지 않는다 |
| 같은 식별자로 두 요청이 동시에 옴 | 두 번째 요청은 구매자 잠금에서 첫 요청이 끝나기를 기다린 뒤 기록한 결과를 받는다. 주문은 하나만 생긴다 |
| 처리 중 오류(`5xx`)로 되돌려진 요청 | 기록하지 않는다. 같은 식별자로 다시 오면 새로 처리한다 |
| `400`, `401`, `403`, `404`, `422`로 끝난 요청 | 기록하지 않는다. 선점 규칙을 판단하기 전에 끝난 요청이다 |
| 기억 기간 | 24시간이다. 지나면 기록을 지운다 |

"같은 내용"은 `showtimeId`, `quantity` 또는 `seatIds`(순서 무관), `buyerName`(앞뒤 공백 제외)이 모두 같은 것이다.

### 화면이 할 일

| 상황 | 화면 동작 |
|---|---|
| 선점을 누름 | 새 식별자를 만든다. 식별자, 요청 내용, `buyerId`, 만든 시각(서버 시각으로 보정한 값)을 `sessionStorage`에 보관한 뒤 보낸다 |
| 응답이나 다시 보내기를 기다리는 중 | 선점 버튼을 막는다. 선점 요청은 한 번에 하나만 진행한다 |
| `201` | 끝난 요청이다. 보관한 요청을 지우고 주문 화면으로 간다 |
| `400`, `404`, `409`, `422` | 끝난 요청이다. 보관한 요청을 지운다. 다시 선점할 때는 자동 재시도를 포함해 새 식별자를 쓴다 |
| `401` | 서버가 요청을 처리하지 않았고, 앞서 보낸 같은 요청의 결과도 알려 주지 않는다. 보관한 요청은 그대로 두고 카카오 로그인으로 간다. 돌아와서 `GET /api/me`의 `buyerId`가 보관한 값과 같으면 같은 식별자와 같은 내용으로 다시 보내 결과를 확인한다. 다르면 보관한 요청을 버린다 |
| `403` | CSRF 토큰이 없거나 틀려서 서버가 요청을 처리하지 않았다. 앞서 보낸 같은 요청의 결과도 알려 주지 않으므로 보관한 요청은 그대로 둔다. `GET /api/me`로 CSRF 쿠키를 다시 받은 뒤 같은 식별자와 같은 내용으로 다시 보낸다. 이 재전송도 아래 다시 보내기 횟수에 넣고, 횟수를 넘으면 결과를 모르는 경우처럼 안내한다 |
| 응답을 받지 못함, 시간 초과(10초), `5xx` | 결과를 모른다. 같은 식별자와 같은 내용으로 1초 뒤, 그다음 2초 뒤에 다시 보낸다(최대 2번). 그래도 모르면 "결과를 확인하지 못했다"는 안내와 함께 내 주문 보기와 다시 시도를 보여 준다. 다시 시도는 같은 식별자로 보낸다 |
| 새로고침하거나 화면을 다시 엶 | 보관한 요청이 있고 `buyerId`가 같으면 같은 식별자로 다시 보내 결과를 확인한다 |

같은 식별자로 다시 보내기 직전에는 매번 보관한 요청을 만든 지 23시간이 지났는지 확인한다. 자동 재전송, 수동 다시 시도, 로그인이나 CSRF 쿠키를 복구한 뒤의 재전송, 새로고침 뒤의 확인이 모두 해당한다.

- 23시간이 지났으면 보내지 않는다. 보관한 요청을 버리고 내 주문에서 확인하게 한다
- 서버가 기록을 지운 뒤에 같은 식별자를 보내면 서버는 처음 보는 요청으로 처리해 주문을 하나 더 만들 수 있다. 그래서 화면의 기준은 서버의 기억 기간(24시간)보다 1시간 짧게 둔다. 서버와 기기의 시계가 조금 달라도 서버에 기록이 남아 있을 때만 보내게 된다

### 주문이 없을 때의 안내

| 경우 | 화면이 아는 것 | 안내 |
|---|---|---|
| 처리 실패 | `400`, `404`, `409`, `422` 응답을 받았다 | 7절의 실패 안내 |
| 아직 처리 중 | 응답을 기다리는 중이다. 서버는 같은 구매자의 요청을 잠금으로 차례로 처리하므로 "처리 중" 응답을 따로 주지 않는다 | 선점 버튼에 "결과를 확인하는 중" |
| 결과를 알 수 없음 | 다시 보내기 한도까지 응답이 없거나 `5xx`다 | "결과를 확인하지 못했다", 내 주문 보기, 다시 시도 |
| 기억 기간이 지남 | 보관한 요청을 만든 지 23시간이 넘었다 | 내 주문에서 확인 |

### 식별자로 주문 찾기

- 같은 식별자로 다시 보내면 그 요청이 만든 주문을 돌려준다. 같은 회차·같은 수량의 다른 주문과 섞이지 않는다
- 그래서 식별자로 주문을 찾는 API를 따로 두지 않는다. 기억 기간이 지난 뒤에는 내 주문 목록에서 찾는다

### 시간 한도

| 항목 | 값 |
|---|---|
| 요청 하나의 시간 제한 | 10초 |
| 결과를 모를 때 다시 보내기 | 최대 2번, 1초·2초 뒤 |
| 판매 전 응답 뒤 자동 재시도 | 1.5초 간격, 최대 5번, 매번 새 식별자 |
| 서버가 결과를 기억하는 기간 | 24시간 |
| 화면이 같은 식별자로 보낼 수 있는 기간 | 보관한 지 23시간까지 |

## 9. 기능 명세와의 대조

[기능 명세](booking-flow.md) 8절 "화면마다 필요한 데이터" 표의 항목이 어느 응답에 있는지 적었다.

| 화면 | 필요한 데이터 | API와 필드 |
|---|---|---|
| 공연 페이지 | 공연 제목·장소 | `GET /api/performances/{id}`의 `title`, `venue` |
| 공연 페이지 | 회차 목록(공연 시작·종료, 판매 시작·마감, 좌석 종류, 판매 상태, 남은 수, 1인 최대 좌석 수) | 같은 응답의 `showtimes[]` |
| 공연 페이지 | 서버 현재 시각 | `serverTime` |
| 모든 화면 | 로그인 상태, 닉네임 | `GET /api/me`의 상태 코드와 `nickname` |
| 수량 선택 | 남은 수, 이 회차에서 이미 가진 수 | `GET /api/showtimes/{id}/availability`의 `remaining`, `ownedCount` |
| 내 주문 | 주문 목록 | `GET /api/me/orders`의 `orders` |
| 주문 | 주문 상태, 입장번호, 회차 정보, 선점 기한, 서버 현재 시각, 주문 토큰, 보는 사람이 주문을 만든 구매자인지 | 주문 응답의 `status`, `entryNumbers`, `showtime`, `heldUntil`, `serverTime`, `token`, `isOwner` |

기능 명세 6절과 7절의 실패 상황은 2절 공통 실패와 7절 표에 모두 있다. 6절의 `403 ORDER_NOT_OWNED`는 7절 결정대로 `404`로 바뀌었다.

## 10. 지금 구현된 API와 달라지는 점

| 구분 | 지금 | 바뀐 뒤 | 구현하는 이슈 |
|---|---|---|---|
| 바뀜 | `POST /api/orders` 본문에 `userId`, `seatIds` | 로그인 세션으로 구매자를 알아낸다. 본문은 `showtimeId`, `quantity` 또는 `seatIds`, `buyerName`. `Idempotency-Key` 헤더 | 카카오 로그인, 자유석 자동 배정 |
| 바뀜 | 선점 응답 `orderId`, `showtimeId`, `status`, `heldUntil`, `seatIds` | 6절의 주문 응답 | 자유석 자동 배정 |
| 바뀜 | `POST /api/orders/{orderId}/confirm`, 본문에 `userId` | `POST /api/orders/{token}/confirm`, 본문 없음, 로그인 | 카카오 로그인 |
| 바뀜 | 다른 구매자의 주문 확정은 `403 ORDER_NOT_OWNED` | `404` | 카카오 로그인 |
| 바뀜 | 실패 응답에 `serverTime` 없음 | 모든 JSON 응답에 `serverTime` | 공연 페이지 조회 |
| 새로 생김 | | `GET /api/me`, `POST /api/logout`, 카카오 로그인 이동 | 카카오 로그인 |
| 새로 생김 | | `GET /api/orders/{token}`, `GET /api/me/orders` | 카카오 로그인 |
| 새로 생김 | | `POST /api/orders/{token}/cancel` | 선점 취소 |
| 새로 생김 | | `GET /api/performances/{id}`, `GET /api/showtimes/{id}/availability` | 공연 페이지 조회 |
| 새로 생김 | | `SALES_CLOSED` | 판매 마감 |
| 새로 생김 | | `ORDER_CANCELLED`, `ORDER_CONFIRMED` | 선점 취소 |
| 새로 생김 | | `IDEMPOTENCY_KEY_REUSED` | 자유석 자동 배정 |
| 없어짐 | 요청 본문의 `userId` | | 카카오 로그인 |
| 없어짐 | `ORDER_NOT_OWNED` | | 카카오 로그인 |
| 유지 | `GET /api/showtimes/{showtimeId}/seats` | 지정석 좌석 배치(P3)와 함께 바꾼다 | |

### 구매자 화면(`frontend/`)과 다른 점

구매자 화면은 이 명세보다 먼저 목 API로 만들었다. 화면을 이 명세에 맞추는 작업은 따로 한다.

| 화면의 지금 | 이 명세 |
|---|---|
| 공연 경로 `/api/shows/{id}` | `/api/performances/{id}`. 응답의 최상위 필드(`id`, `title`, `venue` 등)는 같다 |
| 주문 응답과 내 주문 목록 안의 `show` 객체 | `performance` 객체. 안의 필드(`id`, `title`, `venue`)는 같다 |
| `GET /api/me`에 `nickname`만 | `buyerId` 추가 |
| 목 로그인(`/api/auth/mock-login`) | `/oauth2/authorization/kakao` 이동, `POST /api/logout` |
| CSRF 헤더 없음 | `POST`에 `X-XSRF-TOKEN` |
| 선점 요청 식별자를 메모리에만 둠 | `sessionStorage`에 보관, `401` 뒤와 새로고침 뒤 같은 식별자로 결과 확인 |
| 기한 지난 `HELD`를 화면이 `EXPIRED`로 봄 | 서버가 `EXPIRED`로 준다. 화면의 판단은 그대로 둬도 결과가 같다 |
