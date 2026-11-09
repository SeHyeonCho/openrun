type CalendarEvent = {
    uid: string;
    title: string;
    location: string;
    startsAt: string;
    endsAt: string;
    url: string;
    description: string;
    stampedAt: string;
};

const toIcsTime = (iso: string) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');

const escapeText = (value: string) =>
    value.replace(/\\/g, '\\\\').replace(/\r\n|\r|\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');

function fold(line: string) {
    const bytes = new TextEncoder().encode(line);
    if (bytes.length <= 75) {
        return line;
    }
    const parts: string[] = [];
    let current = '';
    for (const char of line) {
        const limit = parts.length === 0 ? 75 : 74;
        if (new TextEncoder().encode(current + char).length > limit) {
            parts.push(current);
            current = char;
        } else {
            current += char;
        }
    }
    parts.push(current);
    return parts.join('\r\n ');
}

export function buildIcs(event: CalendarEvent) {
    const lines = [
        'BEGIN:VCALENDAR',
        'VERSION:2.0',
        'PRODID:-//OpenRun//Ticket//KO',
        'CALSCALE:GREGORIAN',
        'METHOD:PUBLISH',
        'BEGIN:VEVENT',
        `UID:${event.uid}`,
        `DTSTAMP:${toIcsTime(event.stampedAt)}`,
        `DTSTART:${toIcsTime(event.startsAt)}`,
        `DTEND:${toIcsTime(event.endsAt)}`,
        `SUMMARY:${escapeText(event.title)}`,
        `LOCATION:${escapeText(event.location)}`,
        `DESCRIPTION:${escapeText(event.description)}`,
        `URL:${event.url}`,
        'BEGIN:VALARM',
        'ACTION:DISPLAY',
        `DESCRIPTION:${escapeText(event.title)}`,
        'TRIGGER:-PT3H',
        'END:VALARM',
        'END:VEVENT',
        'END:VCALENDAR',
    ];
    return `${lines.map(fold).join('\r\n')}\r\n`;
}
