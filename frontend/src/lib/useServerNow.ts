import { useEffect, useState } from 'react';
import { serverNow } from './clock';

const TICK_MS = 250;

export function useServerNow(active = true) {
    const [now, setNow] = useState(serverNow);
    useEffect(() => {
        if (!active) {
            return undefined;
        }
        setNow(serverNow());
        const id = setInterval(() => setNow(serverNow()), TICK_MS);
        return () => clearInterval(id);
    }, [active]);
    return now;
}
