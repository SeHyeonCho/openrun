import { describe, expect, it } from 'vitest';
import { api } from '../api/queries';
import { loadDb, saveDb } from './db';
import { setMockLoggedIn, storeScenario } from './scenarios';

describe('목 API', () => {
    it('선점 요청이 겹쳐도 서로의 주문을 덮어쓰지 않고 다른 입장번호를 배정한다', async () => {
        storeScenario('default');
        setMockLoggedIn(true);
        const db = loadDb();
        db.orders = db.orders.filter((o) => o.token !== 'held-demo');
        saveDb(db);

        const [first, second] = await Promise.all([
            api.createOrder({ showtimeId: 12, quantity: 1, buyerName: '밤산책' }),
            api.createOrder({ showtimeId: 12, quantity: 1, buyerName: '밤산책' }),
        ]);

        expect(first.entryNumbers).not.toEqual(second.entryNumbers);
        const tokens = loadDb().orders.map((o) => o.token);
        expect(tokens).toContain(first.token);
        expect(tokens).toContain(second.token);
    });
});
