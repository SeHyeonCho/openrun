let offsetMs = 0;

export function syncServerTime(serverTime: string, sentAt: number, receivedAt: number) {
    const server = Date.parse(serverTime);
    if (Number.isNaN(server)) {
        return;
    }
    offsetMs = server - (sentAt + receivedAt) / 2;
}

export function resetServerTime() {
    offsetMs = 0;
}

export function serverOffsetMs() {
    return offsetMs;
}

export function serverNow() {
    return Date.now() + offsetMs;
}

export function msUntil(target: string, now = serverNow()) {
    return Math.max(0, Date.parse(target) - now);
}

export function hasReached(target: string, now = serverNow()) {
    return now >= Date.parse(target);
}

const pad = (n: number) => String(n).padStart(2, '0');

export function formatCountdown(ms: number) {
    const totalSeconds = Math.ceil(Math.max(0, ms) / 1000);
    const days = Math.floor(totalSeconds / 86_400);
    const hours = Math.floor((totalSeconds % 86_400) / 3_600);
    const minutes = Math.floor((totalSeconds % 3_600) / 60);
    const seconds = totalSeconds % 60;
    if (days > 0) {
        return `${days}일 ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    }
    if (hours > 0) {
        return `${hours}:${pad(minutes)}:${pad(seconds)}`;
    }
    return `${pad(minutes)}:${pad(seconds)}`;
}
