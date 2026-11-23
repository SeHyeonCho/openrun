const RETURN_KEY = 'openrun.login.returnTo';

const isAppPath = (path: string) => path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\');

function storage(): Storage | null {
    try {
        return window.sessionStorage;
    } catch {
        return null;
    }
}

export function saveLoginReturn(path: string) {
    try {
        storage()?.setItem(RETURN_KEY, path);
    } catch {
        // 저장하지 못하면 로그인 뒤 첫 화면에 머문다
    }
}

export function peekLoginReturn(): string | null {
    const path = storage()?.getItem(RETURN_KEY) ?? null;
    return path !== null && isAppPath(path) ? path : null;
}

export function clearLoginReturn() {
    storage()?.removeItem(RETURN_KEY);
}
