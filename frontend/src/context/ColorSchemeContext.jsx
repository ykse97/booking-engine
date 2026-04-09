import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useLayoutEffect,
    useMemo,
    useState
} from 'react';

const COLOR_SCHEME_STORAGE_KEY = 'royal-chair-color-scheme';
const DEFAULT_COLOR_SCHEME = 'dark';

const ColorSchemeContext = createContext(null);

function resolveStoredColorScheme() {
    if (typeof window === 'undefined') {
        return DEFAULT_COLOR_SCHEME;
    }

    const storedColorScheme = window.localStorage.getItem(COLOR_SCHEME_STORAGE_KEY);

    return storedColorScheme === 'light' || storedColorScheme === 'dark'
        ? storedColorScheme
        : DEFAULT_COLOR_SCHEME;
}

function applyColorSchemeToDocument(colorScheme) {
    if (typeof document === 'undefined') {
        return;
    }

    document.documentElement.dataset.theme = colorScheme;
    document.documentElement.style.colorScheme = colorScheme;
    document.body?.setAttribute('data-theme', colorScheme);
}

if (typeof document !== 'undefined') {
    applyColorSchemeToDocument(resolveStoredColorScheme());
}

export function ColorSchemeProvider({ children }) {
    const [colorScheme, setColorScheme] = useState(() => resolveStoredColorScheme());

    useLayoutEffect(() => {
        applyColorSchemeToDocument(colorScheme);
    }, [colorScheme]);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }

        window.localStorage.setItem(COLOR_SCHEME_STORAGE_KEY, colorScheme);
    }, [colorScheme]);

    const toggleColorScheme = useCallback(() => {
        setColorScheme((currentColorScheme) =>
            currentColorScheme === 'dark' ? 'light' : 'dark'
        );
    }, []);

    const value = useMemo(
        () => ({
            colorScheme,
            isDarkMode: colorScheme === 'dark',
            setColorScheme,
            toggleColorScheme
        }),
        [colorScheme, toggleColorScheme]
    );

    return (
        <ColorSchemeContext.Provider value={value}>
            {children}
        </ColorSchemeContext.Provider>
    );
}

export function useColorScheme() {
    const contextValue = useContext(ColorSchemeContext);

    if (!contextValue) {
        throw new Error('useColorScheme must be used inside ColorSchemeProvider.');
    }

    return contextValue;
}
