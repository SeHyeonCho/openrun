import { copy } from '../copy';
import { Button } from './Button';
import styles from './LoadState.module.css';
import { Notice } from './Notice';

export function Spinner({ size = 24 }: { size?: number }) {
    return (
        <svg className={styles.spinner} width={size} height={size} viewBox="0 0 40 40" aria-hidden="true">
            <circle cx="20" cy="20" r="16" fill="none" stroke="var(--color-line)" strokeWidth="4" />
            <path d="M20 4a16 16 0 0 1 16 16" fill="none" stroke="var(--color-ink)" strokeWidth="4" strokeLinecap="round" />
        </svg>
    );
}

export function Loading() {
    return (
        <div className={styles.loading} role="status">
            <Spinner />
            <span className="t-caption">{copy.common.loading}</span>
        </div>
    );
}

export function LoadError({ onRetry }: { onRetry: () => void }) {
    return (
        <Notice title={copy.common.loadFailed} body={copy.common.loadFailedBody}>
            <Button variant="secondary" onClick={onRetry}>
                {copy.common.retry}
            </Button>
        </Notice>
    );
}
