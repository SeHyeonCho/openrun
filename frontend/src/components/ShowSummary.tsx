import { isLatinTitle } from '../lib/format';
import styles from './ShowSummary.module.css';

export function ShowSummary({ title, when, where }: { title: string; when: string; where: string }) {
    return (
        <section className={styles.summary}>
            <span className={`${styles.title} ${isLatinTitle(title) ? styles.latin : ''}`}>{title}</span>
            <span className="t-data">{when}</span>
            <span className="t-caption">{where}</span>
        </section>
    );
}
