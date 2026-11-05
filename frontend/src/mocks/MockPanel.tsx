import { useState } from 'react';
import { api } from '../api/queries';
import styles from './MockPanel.module.css';
import { currentScenario, isMockLoggedIn, scenarios } from './scenarios';

const shortcuts = [
    { href: '/shows/1', label: '공연 · 회차 여럿' },
    { href: '/shows/2', label: '공연 · 회차 하나, 30초 뒤 판매' },
    { href: '/orders/held-demo', label: '주문 · 선점 중' },
    { href: '/orders/confirmed-demo', label: '주문 · 확정' },
    { href: '/orders/expired-demo', label: '주문 · 기한 지남' },
    { href: '/orders/cancelled-demo', label: '주문 · 취소' },
    { href: '/orders/viewer-demo', label: '주문 · 다른 사람 링크' },
    { href: '/orders/unknown-token', label: '주문 · 없는 주문' },
    { href: '/me/orders', label: '내 주문' },
];

export function MockPanel() {
    const scenario = currentScenario();
    const [loggedIn, setLoggedIn] = useState(isMockLoggedIn);
    const label = scenarios.find((s) => s.id === scenario)?.label;

    const changeScenario = (id: string) => {
        const url = new URL(window.location.href);
        url.searchParams.set('mock', id);
        window.location.assign(url);
    };

    const toggleLogin = async () => {
        if (loggedIn) {
            await api.logout();
        } else {
            await api.mockLogin();
        }
        setLoggedIn(!loggedIn);
        window.location.reload();
    };

    return (
        <details className={styles.panel}>
            <summary className={styles.summary}>
                <span className={styles.tag}>목 데이터</span>
                <span>{label}</span>
                <span>· {loggedIn ? '로그인함' : '로그인 전'}</span>
            </summary>
            <div className={styles.body}>
                <label>
                    <span className="visually-hidden">목 시나리오</span>
                    <select className={styles.select} value={scenario} onChange={(e) => changeScenario(e.target.value)}>
                        {scenarios.map((s) => (
                            <option key={s.id} value={s.id}>
                                {s.label}
                            </option>
                        ))}
                    </select>
                </label>
                <div className={styles.row}>
                    <button type="button" className={styles.chip} onClick={toggleLogin}>
                        {loggedIn ? '로그아웃' : '로그인'}
                    </button>
                    <button type="button" className={styles.chip} onClick={() => changeScenario(scenario)}>
                        목 데이터 처음으로
                    </button>
                </div>
                <div className={styles.row}>
                    {shortcuts.map((s) => (
                        <a key={s.href} className={styles.chip} href={s.href}>
                            {s.label}
                        </a>
                    ))}
                </div>
            </div>
        </details>
    );
}
