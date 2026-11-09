import { useState } from 'react';
import { copy } from '../copy';
import styles from './KakaoLoginButton.module.css';

export function KakaoLoginButton({ onLogin }: { onLogin: () => Promise<void> }) {
    const [busy, setBusy] = useState(false);
    const click = async () => {
        setBusy(true);
        try {
            await onLogin();
        } finally {
            setBusy(false);
        }
    };
    return (
        <button type="button" className={styles.button} onClick={click} disabled={busy}>
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                <path fill="#000000" d="M12 3.5C6.75 3.5 2.5 6.86 2.5 11c0 2.66 1.76 5 4.41 6.33l-.9 3.3c-.08.3.26.54.52.37l3.93-2.6c.5.06 1.01.1 1.54.1 5.25 0 9.5-3.36 9.5-7.5S17.25 3.5 12 3.5z" />
            </svg>
            {copy.login.kakao}
        </button>
    );
}
