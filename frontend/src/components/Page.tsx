import type { ReactNode } from 'react';
import styles from './Page.module.css';

type PageProps = {
    header: ReactNode;
    bottomBar?: ReactNode;
    children: ReactNode;
};

export function Page({ header, bottomBar, children }: PageProps) {
    return (
        <div className={`${styles.page} ${bottomBar ? styles.withBottomBar : ''}`}>
            {header}
            <main className={styles.main}>{children}</main>
            {bottomBar}
        </div>
    );
}
