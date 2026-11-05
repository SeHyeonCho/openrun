import type { SalesStatus, SeatKind } from './api/types';

export const copy = {
    common: {
        myOrders: '내 주문',
        back: '이전 화면으로',
        close: '닫기',
        loading: '불러오는 중이에요',
        loadFailed: '정보를 불러오지 못했어요',
        loadFailedBody: '연결이 고르지 않을 수 있어요. 잠시 뒤 다시 시도해 주세요.',
        retry: '다시 시도',
        unknownError: '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.',
        loginFailed: '로그인하지 못했어요. 다시 시도해 주세요.',
    },
    login: {
        kakao: '카카오 로그인',
        note: '따로 가입하지 않아요. 카카오 계정의 닉네임만 받아요.',
    },
    seatKind: { FREE: '자유석', RESERVED: '지정석' } satisfies Record<SeatKind, string>,
    salesStatus: {
        BEFORE_SALE: '판매 전',
        ON_SALE: '판매 중',
        SOLD_OUT: '매진',
        SALES_CLOSED: '판매 마감',
    } satisfies Record<SalesStatus, string>,
    notFoundPage: {
        title: '페이지를 찾을 수 없어요',
        body: '주소가 잘렸거나 잘못 복사됐을 수 있어요. 받은 링크를 다시 확인해 주세요.',
    },
    showPage: {
        notFound: '공연을 찾을 수 없어요',
        notFoundBody: '링크가 잘렸거나 잘못 복사됐을 수 있어요. 받은 링크를 다시 확인해 주세요.',
        posterAlt: (title: string) => `${title} 포스터`,
        showtimeCount: (count: number) => `회차 ${count}개`,
        showtimes: '회차',
        note: {
            BEFORE_SALE: (opensAt: string, max: number) => `${opensAt} 판매 시작 · 1인 최대 ${max}매`,
            ON_SALE: (remaining: number, max: number) => `남은 수 ${remaining} · 1인 최대 ${max}매`,
            SOLD_OUT: (max: number) => `1인 최대 ${max}매`,
            SALES_CLOSED: '판매가 끝났어요',
        },
        login: {
            beforeSaleTitle: '판매 전에 로그인해 두세요',
            beforeSaleBody: '로그인해 두면 판매가 열린 순간 수량만 고르고 바로 선점할 수 있어요.',
            title: '카카오 계정으로 예매해요',
            body: '로그인하면 수량만 고르고 바로 선점할 수 있어요.',
        },
        guide: {
            title: '예매 안내',
            entry: '입장번호는 남은 번호 가운데 앞 번호부터 자동으로 배정돼요.',
            hold: (minutes: number) => `선점한 뒤 ${minutes}분 안에 확정해야 예매가 끝나요.`,
            limit: (max: number) => `한 회차에서 1인 최대 ${max}매까지 가질 수 있어요.`,
            limitPerShowtime: '1인 최대 매수는 회차마다 따로 세요.',
        },
        cta: {
            pick: '회차를 골라 주세요',
            pickNote: '회차를 고르면 예매 버튼이 열려요.',
            beforeSale: '판매 시작까지',
            beforeSaleNote: (opensAt: string) => `${opensAt}에 판매가 열려요. 새로고침하지 않아도 돼요.`,
            book: '예매하기',
            bookNote: '누르면 수량을 골라요.',
            bookNoteLoggedOut: '카카오 로그인을 먼저 거친 뒤 수량을 골라요.',
            soldOut: '매진',
            soldOutNote: '선점 기한이 지난 표가 다시 풀리면 예매할 수 있어요.',
            closed: '판매 마감',
            closedNote: '이 회차는 판매가 끝났어요.',
        },
    },
};
