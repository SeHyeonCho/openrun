import type { ReactNode } from 'react';
import styles from './BottomBar.module.css';

export function BottomBar({ children, note }: { children: ReactNode; note?: ReactNode }) {
    return (
        <div className={styles.bar}>
            {children}
            {note && <p className={`${styles.note} t-caption`}>{note}</p>}
        </div>
    );
}
