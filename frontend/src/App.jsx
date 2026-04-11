import { Suspense, lazy, useEffect, useLayoutEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import {
    cancelActiveScrollSequence,
    scrollWindowToElement,
    scrollWindowToTop,
    setActiveScrollSequence
} from './utils/scroll';
import {
    PENDING_SCROLL_KEY,
    clearPendingNavigation,
    setSectionScrollPending
} from './utils/navigation';

const Services = lazy(() => import('./pages/Services'));
const Faq = lazy(() => import('./pages/Faq'));
const Booking = lazy(() => import('./pages/Booking'));

function runTopAlignmentSequence() {
    cancelActiveScrollSequence();

    const timeoutIds = [];
    const frameIds = [];

    const alignTop = () => {
        scrollWindowToTop({ behavior: 'instant' });
    };

    alignTop();

    frameIds.push(
        window.requestAnimationFrame(() => {
            alignTop();
        })
    );

    frameIds.push(
        window.requestAnimationFrame(() => {
            frameIds.push(
                window.requestAnimationFrame(() => {
                    alignTop();
                })
            );
        })
    );

    [0, 60, 140].forEach((delay) => {
        timeoutIds.push(
            window.setTimeout(() => {
                alignTop();
            }, delay)
        );
    });

    return () => {
        frameIds.forEach((id) => window.cancelAnimationFrame(id));
        timeoutIds.forEach((id) => window.clearTimeout(id));
    };
}

function runElementAlignmentSequence(sectionId) {
    cancelActiveScrollSequence();

    const timeoutIds = [];
    const frameIds = [];
    let cancelled = false;

    const alignToTarget = () => {
        if (cancelled) {
            return;
        }

        const target = document.getElementById(sectionId);

        if (target) {
            scrollWindowToElement(target, {
                behavior: 'instant',
                extraOffset: 16
            });
        }
    };

    const scheduleAlign = (delay) => {
        timeoutIds.push(
            window.setTimeout(() => {
                alignToTarget();
            }, delay)
        );
    };

    alignToTarget();

    frameIds.push(
        window.requestAnimationFrame(() => {
            alignToTarget();
        })
    );

    frameIds.push(
        window.requestAnimationFrame(() => {
            frameIds.push(
                window.requestAnimationFrame(() => {
                    alignToTarget();
                })
            );
        })
    );

    [
        0,
        60,
        140,
        240,
        360,
        520,
        720,
        980,
        1280,
        1700,
        2200,
        2800,
        3600,
        4600,
        5800
    ].forEach(scheduleAlign);

    let disposeSequence = null;

    const stopForUserIntent = () => {
        disposeSequence?.();
    };

    window.addEventListener('wheel', stopForUserIntent, { passive: true, once: true });
    window.addEventListener('touchstart', stopForUserIntent, { passive: true, once: true });
    window.addEventListener('keydown', stopForUserIntent, { once: true });

    const cleanup = () => {
        cancelled = true;
        frameIds.forEach((id) => window.cancelAnimationFrame(id));
        timeoutIds.forEach((id) => window.clearTimeout(id));
        window.removeEventListener('wheel', stopForUserIntent);
        window.removeEventListener('touchstart', stopForUserIntent);
        window.removeEventListener('keydown', stopForUserIntent);
    };

    disposeSequence = setActiveScrollSequence(cleanup);
    timeoutIds.push(
        window.setTimeout(() => {
            disposeSequence?.();
        }, 6200)
    );

    return disposeSequence;
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

        if (!raw) {
            clearPendingNavigation();
            return undefined;
        }

        let parsed;

        try {
            parsed = JSON.parse(raw);
        } catch {
            clearPendingNavigation();
            return runTopAlignmentSequence();
        }

        if (!parsed?.path) {
            clearPendingNavigation();
            return undefined;
        }

        if (parsed.path !== location.pathname) {
            setSectionScrollPending(true);
            return undefined;
        }

        let cancelled = false;
        let timerId = null;
        let cleanupAlignment = null;
        let locateAttempts = 0;
        const maxLocateAttempts = 40;

        const finish = () => {
            sessionStorage.removeItem(PENDING_SCROLL_KEY);

            window.requestAnimationFrame(() => {
                if (!cancelled) {
                    clearPendingNavigation();
                }
            });
        };

        const sectionId = parsed.sectionId || null;

        if (!sectionId) {
            cleanupAlignment = runTopAlignmentSequence();
            timerId = window.setTimeout(() => {
                if (!cancelled) {
                    finish();
                }
            }, 170);

            return () => {
                cancelled = true;
                if (timerId) {
                    window.clearTimeout(timerId);
                }
                if (cleanupAlignment) {
                    cleanupAlignment();
                }
            };
        }

        setSectionScrollPending(true);

        const tryAlign = () => {
            if (cancelled) {
                return;
            }

            const target = document.getElementById(sectionId);

            if (target) {
                cleanupAlignment = runElementAlignmentSequence(sectionId);
                timerId = window.setTimeout(() => {
                    if (!cancelled) {
                        finish();
                    }
                }, 1400);
                return;
            }

            if (locateAttempts >= maxLocateAttempts) {
                cleanupAlignment = runTopAlignmentSequence();
                timerId = window.setTimeout(() => {
                    if (!cancelled) {
                        finish();
                    }
                }, 260);
                return;
            }

            locateAttempts += 1;
            timerId = window.setTimeout(tryAlign, 50);
        };

        tryAlign();

        return () => {
            cancelled = true;
            if (timerId) {
                window.clearTimeout(timerId);
            }
            if (cleanupAlignment) {
                cleanupAlignment();
            }
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

function RouteFallback({ pathname }) {
    const isBookingRoute = pathname === '/booking';

    return (
        <section className="services-page-shell py-10 sm:py-12 lg:py-14" aria-hidden="true">
            <div
                className={`mx-auto w-full ${isBookingRoute ? 'max-w-[960px] min-h-[72vh]' : 'max-w-[760px] min-h-[56vh]'
                    }`}
            />
        </section>
    );
}

export default function App() {
    const location = useLocation();

    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
        const adminSubpath =
            window.location.pathname === '/admin'
                ? '/bookings'
                : window.location.pathname.replace(/^\/admin/, '') || '/bookings';
        const normalizedAdminSubpath = adminSubpath.startsWith('/')
            ? adminSubpath
            : `/${adminSubpath}`;
        const queryString = window.location.search || '';

        window.location.replace(`/admin.html${queryString}#${normalizedAdminSubpath}`);
        return null;
    }

    return (
        <Layout>
            <Suspense fallback={<RouteFallback pathname={location.pathname} />}>
                <Routes>
                    <Route path="/" element={<Home />} />
                    <Route path="/services" element={<Services />} />
                    <Route path="/booking" element={<Booking />} />
                    <Route path="/faq" element={<Faq />} />
                </Routes>
            </Suspense>
        </Layout>
    );
}
