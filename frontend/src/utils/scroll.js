const DEFAULT_SITE_HEADER_HEIGHT = 108;
const AUTO_SCROLL_HIDDEN_CLASS = 'auto-scroll-hidden';

let autoScrollHideTimeoutId = null;

function parseHeaderHeight(value) {
    const numericValue = Number.parseFloat(String(value || '').trim());
    return Number.isFinite(numericValue) && numericValue > 0
        ? numericValue
        : DEFAULT_SITE_HEADER_HEIGHT;
}

export function getSiteHeaderHeight() {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
        return DEFAULT_SITE_HEADER_HEIGHT;
    }

    return parseHeaderHeight(
        window.getComputedStyle(document.documentElement).getPropertyValue('--site-header-height')
    );
}

export function getSiteHeaderOffset(extraOffset = 16) {
    return getSiteHeaderHeight() + extraOffset;
}

export function getScrollTopForElement(element, extraOffset = 16) {
    if (typeof window === 'undefined' || !element) {
        return null;
    }

    return Math.max(
        0,
        element.getBoundingClientRect().top + window.scrollY - getSiteHeaderOffset(extraOffset)
    );
}

export function setSiteHeaderHeight(height) {
    if (typeof document === 'undefined') {
        return;
    }

    const normalizedHeight =
        Number.isFinite(height) && height > 0 ? height : DEFAULT_SITE_HEADER_HEIGHT;
    document.documentElement.style.setProperty('--site-header-height', `${normalizedHeight}px`);
}

export function setAutoScrollHidden(isHidden) {
    if (typeof document === 'undefined') {
        return;
    }

    document.documentElement.classList.toggle(AUTO_SCROLL_HIDDEN_CLASS, isHidden);
}

export function hideAutoScrollbarsTemporarily(duration = 520) {
    if (typeof window === 'undefined') {
        return;
    }

    setAutoScrollHidden(true);

    if (autoScrollHideTimeoutId) {
        window.clearTimeout(autoScrollHideTimeoutId);
    }

    autoScrollHideTimeoutId = window.setTimeout(() => {
        autoScrollHideTimeoutId = null;
        setAutoScrollHidden(false);
    }, duration);
}

function resolveAutoScrollHideDuration(behavior, explicitDuration) {
    if (explicitDuration != null) {
        return explicitDuration;
    }

    return behavior === 'smooth' ? 520 : 180;
}

export function scrollWindowToTop({
    behavior = 'smooth',
    hideScrollbar = false,
    hideScrollbarDuration
} = {}) {
    if (typeof window === 'undefined') {
        return;
    }

    if (hideScrollbar) {
        hideAutoScrollbarsTemporarily(resolveAutoScrollHideDuration(behavior, hideScrollbarDuration));
    }

    window.scrollTo({
        top: 0,
        left: 0,
        behavior
    });
}

export function scrollWindowToElement(
    element,
    { behavior = 'smooth', extraOffset = 16, hideScrollbar = false, hideScrollbarDuration } = {}
) {
    const top = getScrollTopForElement(element, extraOffset);

    if (top == null) {
        return;
    }

    if (hideScrollbar) {
        hideAutoScrollbarsTemporarily(resolveAutoScrollHideDuration(behavior, hideScrollbarDuration));
    }

    window.scrollTo({
        top,
        left: 0,
        behavior
    });
}
