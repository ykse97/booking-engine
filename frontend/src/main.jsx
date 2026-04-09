import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ColorSchemeProvider } from './context/ColorSchemeContext';
import './index.css';
import { primePublicApiOrigin } from './utils/publicApiHints';

primePublicApiOrigin();

ReactDOM.createRoot(document.getElementById('root')).render(
    <BrowserRouter>
        <ColorSchemeProvider>
            <App />
        </ColorSchemeProvider>
    </BrowserRouter>
);
