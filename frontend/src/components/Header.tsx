import { Link } from 'react-router';
import { copy } from '../copy';
import styles from './Header.module.css';

type HeaderProps = {
    title?: string;
    backTo?: string;
    showMyOrders?: boolean;
};

export function Header({ title, backTo, showMyOrders = true }: HeaderProps) {
    return (
        <header className={styles.header}>
            {title ? (
                <>
                    {backTo ? (
                        <Link to={backTo} className={styles.iconLink} aria-label={copy.common.back}>
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <path d="M15 5l-7 7 7 7" />
                            </svg>
                        </Link>
                    ) : (
                        <span />
                    )}
                    <h1 className={styles.title}>{title}</h1>
                </>
            ) : (
                <span className={`${styles.brand} t-wordmark`}>OPENRUN</span>
            )}
            {showMyOrders && (
                <Link to="/me/orders" className={styles.end}>
                    {copy.common.myOrders}
                </Link>
            )}
        </header>
    );
}
