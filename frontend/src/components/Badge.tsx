import styles from './Badge.module.css';

export type BadgeTone = 'accent' | 'outline' | 'solid' | 'muted';

export function Badge({ tone, children }: { tone: BadgeTone; children: string }) {
    return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>;
}
