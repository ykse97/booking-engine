import { useLocation, useNavigate } from 'react-router-dom';
import { scrollWindowToElement, scrollWindowToTop } from '../utils/scroll';

const PENDING_SCROLL_KEY = 'pending-section-scroll';

export default function SectionLink({
    sectionId,
    fallbackPath = '/',
    className = '',
    children,
    onNavigate
}) {
    const location = useLocation();
    const navigate = useNavigate();

    function handleClick(event) {
        event.preventDefault();

        const isSamePage = location.pathname === fallbackPath;

        if (isSamePage) {
            if (!sectionId) {
                scrollWindowToTop({
                    behavior: 'smooth',
                    hideScrollbar: true
                });
                onNavigate?.();
                return;
            }

            const target = document.getElementById(sectionId);
            if (target) {
                scrollWindowToElement(target, {
                    behavior: 'smooth',
                    extraOffset: 16,
                    hideScrollbar: true
                });
            } else {
                scrollWindowToTop({
                    behavior: 'smooth',
                    hideScrollbar: true
                });
            }

            onNavigate?.();
            return;
        }

        sessionStorage.setItem(
            PENDING_SCROLL_KEY,
            JSON.stringify({
                path: fallbackPath,
                sectionId: sectionId || null,
                ts: Date.now()
            })
        );

        onNavigate?.();
        navigate(fallbackPath);
    }

    return (
        <a href={fallbackPath} onClick={handleClick} className={className}>
            {children}
        </a>
    );
}
