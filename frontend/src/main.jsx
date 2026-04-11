import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ColorSchemeProvider } from './context/ColorSchemeContext';
import GlobalErrorBoundary from './components/GlobalErrorBoundary';
import './index.css';
import { primePublicApiOrigin } from './utils/publicApiHints';
import { primeThemeLogoAssets } from './utils/themeAssets';

primePublicApiOrigin();
primeThemeLogoAssets();

ReactDOM.createRoot(document.getElementById('root')).render(
    <GlobalErrorBoundary>
        <BrowserRouter>
            <ColorSchemeProvider>
                <App />
            </ColorSchemeProvider>
        </BrowserRouter>
    </GlobalErrorBoundary>
);
