import { useEffect, useLayoutEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import Services from './pages/Services';
import Faq from './pages/Faq';
import Booking from './pages/Booking';
import { scrollWindowToElement, setAutoScrollHidden } from './utils/scroll';

const PENDING_SCROLL_KEY = 'pending-section-scroll';
const SECTION_SCROLL_PENDING_CLASS = 'section-scroll-pending';
const MOBILE_FORCE_SCROLL_TOP_KEY = 'mobile-force-scroll-top';

function setSectionScrollPending(isPending) {
    if (typeof document === 'undefined') {
        return;
    }

    document.documentElement.classList.toggle(SECTION_SCROLL_PENDING_CLASS, isPending);
    setAutoScrollHidden(isPending);
}

function ScrollToSectionHandler() {
    const location = useLocation();

    useEffect(() => {
        if ('scrollRestoration' in window.history) {
            window.history.scrollRestoration = 'manual';
        }
    }, []);

    useLayoutEffect(() => {
        const raw = sessionStorage.getItem(PENDING_SCROLL_KEY);
        const mobileForceScrollRaw = sessionStorage.getItem(MOBILE_FORCE_SCROLL_TOP_KEY);

        if (!raw && mobileForceScrollRaw) {
            let parsedForceScroll;

            try {
                parsedForceScroll = JSON.parse(mobileForceScrollRaw);
            } catch {
                sessionStorage.removeItem(MOBILE_FORCE_SCROLL_TOP_KEY);
                parsedForceScroll = null;
            }

            if (!parsedForceScroll?.path || parsedForceScroll.path === location.pathname) {
                sessionStorage.removeItem(MOBILE_FORCE_SCROLL_TOP_KEY);
                setSectionScrollPending(false);
                window.scrollTo({ top: 0, left: 0, behavior: 'auto' });

                const frameId = window.requestAnimationFrame(() => {
                    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
                });

                const timeoutId = window.setTimeout(() => {
                    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
                }, 90);

                return () => {
                    window.cancelAnimationFrame(frameId);
                    window.clearTimeout(timeoutId);
                };
            }
        }

        if (!raw) {
            setSectionScrollPending(false);
            window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
            return;
        }

        let parsed;
        try {
            parsed = JSON.parse(raw);
        } catch {
            setSectionScrollPending(false);
            sessionStorage.removeItem(PENDING_SCROLL_KEY);
            window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
            return;
        }

        if (!parsed?.path || parsed.path !== location.pathname) {
            setSectionScrollPending(false);
            return;
        }

        setSectionScrollPending(true);
        window.scrollTo({ top: 0, left: 0, behavior: 'auto' });

        let cancelled = false;
        let timerId = null;
        let locateAttempts = 0;
        let alignAttempts = 0;
        const maxLocateAttempts = 50;
        const maxAlignAttempts = 6;

        const clearPendingScrollRecord = () => {
            sessionStorage.removeItem(PENDING_SCROLL_KEY);
        };

        const finishPendingScroll = (clearRecord = true) => {
            if (clearRecord) {
                clearPendingScrollRecord();
            }

            window.requestAnimationFrame(() => {
                if (!cancelled) {
                    setSectionScrollPending(false);
                }
            });
        };

        const tryScroll = () => {
            if (cancelled) {
                return;
            }

            if (!parsed.sectionId) {
                window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
                finishPendingScroll();
                return;
            }

            const target = document.getElementById(parsed.sectionId);

            if (target) {
                scrollWindowToElement(target, {
                    behavior: 'auto',
                    extraOffset: 16
                });
                alignAttempts += 1;

                if (alignAttempts < maxAlignAttempts) {
                    timerId = window.setTimeout(tryScroll, alignAttempts < 3 ? 160 : 260);
                } else {
                    finishPendingScroll();
                }
            } else if (locateAttempts < maxLocateAttempts) {
                locateAttempts += 1;
                timerId = window.setTimeout(tryScroll, 80);
            } else {
                finishPendingScroll();
                window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
            }
        };

        timerId = window.setTimeout(tryScroll, 120);

        return () => {
            cancelled = true;
            if (timerId) {
                window.clearTimeout(timerId);
            }
            setSectionScrollPending(false);
        };
    }, [location.pathname]);

    return null;
}

function Layout({ children }) {
    return (
        <div className="lux-shell">
            <Navbar />
            <ScrollToSectionHandler />
            <main>{children}</main>
            <Footer />
        </div>
    );
}

export default function App() {
    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
        const adminSubpath =
            window.location.pathname === '/admin'
                ? '/bookings'
                : window.location.pathname.replace(/^\/admin/, '') || '/bookings';
        const normalizedAdminSubpath = adminSubpath.startsWith('/') ? adminSubpath : `/${adminSubpath}`;
        const queryString = window.location.search || '';

        window.location.replace(`/admin.html${queryString}#${normalizedAdminSubpath}`);
        return null;
    }

    return (
        <Layout>
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/services" element={<Services />} />
                <Route path="/booking" element={<Booking />} />
                <Route path="/faq" element={<Faq />} />
            </Routes>
        </Layout>
    );
}
