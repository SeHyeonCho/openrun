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

```json
{
  "buyerId": "b_7Qm2xK9pLr",
  "nickname": "밤산책",
  "serverTime": "2026-11-20T10:58:00Z"
}
```

### `POST /api/logout`

로그인 세션을 끝낸다. 성공하면 `204`이고 본문이 없다. 로그인하지 않은 상태에서 불러도 아무것도 바꾸지 않고 `204`다. CSRF 토큰은 필요하다.

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
| `quantity` | number | 자유석 회차에서 고른 좌석 수. 1 이상이다 |
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

| 상황 | HTTP 상태 |
|---|---|
| 성공 | 200 |
| 없는 주문 | 404 |

### `POST /api/orders/{token}/confirm`

선점을 확정한다. 이번 단계에는 결제가 없어서 이 요청이 결제를 대신한다. 결제(P2)가 들어오면 결제 성공이 확정을 대신 부른다. 주문을 만든 구매자만 할 수 있고, CSRF 토큰이 필요하다. 요청 본문은 없다.

- 이미 확정한 주문을 다시 확정하면 같은 응답(`200`)을 돌려주고 아무것도 바꾸지 않는다. 그래서 결과를 모를 때 같은 주문으로 다시 보내도 된다

| 상황 | HTTP 상태 | `code` |
|---|---|---|
| 확정함, 이미 확정함 | 200 | |
| 없는 주문, 다른 구매자의 주문 | 404 | 없음 |
| 기한이 지났거나 만료된 주문 | 409 | `ORDER_EXPIRED` |
| 취소한 주문 | 409 | `ORDER_CANCELLED` |

### `POST /api/orders/{token}/cancel`

기한 전의 선점을 취소한다. 주문은 `CANCELLED`가 되고 좌석은 바로 다시 팔 수 있다. 주문을 만든 구매자만 할 수 있고, CSRF 토큰이 필요하다. 요청 본문은 없다.

- 이미 취소한 주문을 다시 취소하면 같은 응답(`200`)을 돌려주고 아무것도 바꾸지 않는다

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
