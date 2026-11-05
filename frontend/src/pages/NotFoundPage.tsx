import { Header } from '../components/Header';
import { Notice } from '../components/Notice';
import { Page } from '../components/Page';
import { copy } from '../copy';

export function NotFoundPage() {
    return (
        <Page header={<Header />}>
            <Notice title={copy.notFoundPage.title} body={copy.notFoundPage.body} />
        </Page>
    );
}
