export type ShareResult = 'shared' | 'copied' | 'cancelled' | 'failed';

export async function shareLink({ title, url }: { title: string; url: string }): Promise<ShareResult> {
    if (typeof navigator.share === 'function') {
        try {
            await navigator.share({ title, url });
            return 'shared';
        } catch (error) {
            if (error instanceof DOMException && error.name === 'AbortError') {
                return 'cancelled';
            }
        }
    }
    try {
        await navigator.clipboard.writeText(url);
        return 'copied';
    } catch {
        return 'failed';
    }
}

export function downloadFile(filename: string, href: string) {
    const link = document.createElement('a');
    link.href = href;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
}
