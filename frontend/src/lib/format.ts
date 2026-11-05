const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
});

function partsOf(iso: string) {
    const map: Record<string, string> = {};
    for (const part of formatter.formatToParts(new Date(iso))) {
        map[part.type] = part.value;
    }
    return {
        year: map.year ?? '',
        month: map.month ?? '',
        day: map.day ?? '',
        weekday: (map.weekday ?? '').toUpperCase(),
        hour: map.hour ?? '',
        minute: map.minute ?? '',
    };
}

export function formatDateTime(iso: string, { year = false } = {}) {
    const p = partsOf(iso);
    return `${year ? `${p.year}.` : ''}${p.month}.${p.day} ${p.weekday} ${p.hour}:${p.minute}`;
}

export function formatShowtime(startsAt: string, endsAt: string, { year = false } = {}) {
    const end = partsOf(endsAt);
    return `${formatDateTime(startsAt, { year })}–${end.hour}:${end.minute}`;
}

export function formatDateSpan(startsAts: string[]) {
    const sorted = [...startsAts].sort();
    const first = partsOf(sorted[0] ?? '');
    const last = partsOf(sorted[sorted.length - 1] ?? '');
    const head = `${first.year}.${first.month}.${first.day}`;
    if (first.year === last.year && first.month === last.month && first.day === last.day) {
        return head;
    }
    const tail = first.year === last.year ? `${last.month}.${last.day}` : `${last.year}.${last.month}.${last.day}`;
    return `${head} – ${tail}`;
}

export const isLatinTitle = (title: string) => /^[\x20-\x7E]+$/.test(title);
