import styles from './MockUnavailable.module.css';

export function MockUnavailable() {
    return (
        <p className={styles.notice} role="alert">
            <span className={styles.tag}>목 데이터</span>
            목 API를 시작하지 못했어요. 이 브라우저가 서비스 워커를 막고 있을 수 있어요. 다른 브라우저로 열거나 VITE_API_MOCK=false로 실제 서버에 붙여 주세요.
        </p>
    );
}
