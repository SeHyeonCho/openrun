export const scenarios = [
    { id: 'default', label: '정상 흐름' },
    { id: 'sales-not-open-once', label: '열린 직후 한 번 판매 전 응답' },
    { id: 'sales-closed', label: '선점할 때 판매 마감' },
    { id: 'seat-not-available', label: '고르는 사이 남은 수 2매로 줄어듦' },
    { id: 'sold-out-race', label: '고르는 사이 매진' },
    { id: 'seat-limit', label: '다른 기기에서 먼저 예매해 1인 한도 초과' },
    { id: 'hold-lost-created', label: '선점 응답 한 번 유실, 주문은 만들어짐' },
    { id: 'hold-lost-none', label: '선점 응답 한 번 유실, 주문 없음' },
    { id: 'hold-lost-always', label: '선점 응답이 계속 유실됨' },
    { id: 'short-hold', label: '선점 기한 30초' },
    { id: 'confirm-expired', label: '확정할 때 서버가 기한 지남으로 거부' },
    { id: 'confirm-cancelled', label: '확정할 때 이미 취소됨' },
    { id: 'cancel-confirmed', label: '취소할 때 이미 확정됨' },
    { id: 'confirm-lost-once', label: '확정 응답 한 번 유실' },
    { id: 'slow', label: '모든 응답 2초 지연' },
    { id: 'clock-skew', label: '서버 시계가 기기보다 3분 빠름' },
] as const;

export type ScenarioId = (typeof scenarios)[number]['id'];

const SCENARIO_KEY = 'openrun.mock.scenario';
const LOGIN_KEY = 'openrun.mock.loggedIn';

const isScenarioId = (value: string | null): value is ScenarioId =>
    scenarios.some((scenario) => scenario.id === value);

function storage(): Storage | null {
    try {
        return window.sessionStorage;
    } catch {
        return null;
    }
}

export function currentScenario(): ScenarioId {
    const stored = storage()?.getItem(SCENARIO_KEY) ?? null;
    return isScenarioId(stored) ? stored : 'default';
}

export function storeScenario(id: string) {
    if (isScenarioId(id)) {
        storage()?.setItem(SCENARIO_KEY, id);
    }
}

export function isMockLoggedIn() {
    return storage()?.getItem(LOGIN_KEY) === 'true';
}

export function setMockLoggedIn(loggedIn: boolean) {
    storage()?.setItem(LOGIN_KEY, String(loggedIn));
}
