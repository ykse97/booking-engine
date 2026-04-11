import { createReadStream, existsSync } from 'node:fs';
import { promises as fs } from 'node:fs';
import { createServer } from 'node:http';
import path from 'node:path';
import { brotliCompressSync, constants as zlibConstants, gzipSync } from 'node:zlib';

const DIST_DIR = path.resolve('D:/JavaProjects/booking-engine/frontend/dist');
const API_ORIGIN = 'http://localhost:8080';
const HOST = 'localhost';
const PORT = 4173;

const MIME_TYPES = {
    '.css': 'text/css; charset=utf-8',
    '.html': 'text/html; charset=utf-8',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.mp4': 'video/mp4',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.txt': 'text/plain; charset=utf-8',
    '.avif': 'image/avif',
    '.woff2': 'font/woff2'
};
const COMPRESSIBLE_EXTENSIONS = new Set(['.css', '.html', '.js', '.json', '.svg', '.txt']);

function resolveStaticPath(urlPath) {
    const normalizedPath = decodeURIComponent(urlPath.split('?')[0]);
    const relativePath = normalizedPath === '/' ? '/index.html' : normalizedPath;
    const absolutePath = path.resolve(path.join(DIST_DIR, `.${relativePath}`));

    if (!absolutePath.startsWith(DIST_DIR)) {
        return null;
    }

    return absolutePath;
}

function resolveContentEncoding(acceptEncoding, extension) {
    if (!COMPRESSIBLE_EXTENSIONS.has(extension)) {
        return null;
    }

    if (acceptEncoding.includes('br')) {
        return 'br';
    }

    if (acceptEncoding.includes('gzip')) {
        return 'gzip';
    }

    return null;
}

async function serveStatic(request, response) {
    const requestPath = request.url || '/';
    const staticPath = resolveStaticPath(requestPath);
    const fallbackPath = path.join(DIST_DIR, 'index.html');
    const filePath =
        staticPath && existsSync(staticPath) && !staticPath.endsWith(path.sep)
            ? staticPath
            : fallbackPath;

    try {
        const extension = path.extname(filePath).toLowerCase();
        const fileBuffer = await fs.readFile(filePath);
        const acceptEncoding = String(request.headers['accept-encoding'] || '');
        const contentEncoding = resolveContentEncoding(acceptEncoding, extension);
        const responseBody =
            contentEncoding === 'br'
                ? brotliCompressSync(fileBuffer, {
                    params: {
                        [zlibConstants.BROTLI_PARAM_QUALITY]: 5
                    }
                })
                : contentEncoding === 'gzip'
                    ? gzipSync(fileBuffer, { level: 6 })
                    : fileBuffer;
        const headers = {
            'Content-Length': responseBody.byteLength,
            'Content-Type': MIME_TYPES[extension] || 'application/octet-stream',
            'Cache-Control': filePath === fallbackPath ? 'no-cache' : 'public, max-age=31536000, immutable'
        };

        if (contentEncoding) {
            headers['Content-Encoding'] = contentEncoding;
            headers.Vary = 'Accept-Encoding';
        }

        response.writeHead(200, headers);
        response.end(responseBody);
    } catch {
        response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        response.end('Not found');
    }
}

async function proxyApi(request, response) {
    const targetUrl = new URL(request.url, API_ORIGIN);
    const headers = new Headers();
    const requestBody =
        request.method === 'GET' || request.method === 'HEAD'
            ? undefined
            : await new Promise((resolve, reject) => {
                const chunks = [];
                request.on('data', (chunk) => chunks.push(chunk));
                request.on('end', () => resolve(Buffer.concat(chunks)));
                request.on('error', reject);
            });

    Object.entries(request.headers).forEach(([key, value]) => {
        if (value !== undefined) {
            headers.set(key, Array.isArray(value) ? value.join(', ') : value);
        }
    });

    headers.set('host', new URL(API_ORIGIN).host);

    const upstream = await fetch(targetUrl, {
        method: request.method,
        headers,
        body: requestBody,
        duplex: requestBody ? 'half' : undefined
    });

    const responseHeaders = {};
    upstream.headers.forEach((value, key) => {
        const lowerKey = key.toLowerCase();

        if (lowerKey !== 'content-encoding' && lowerKey !== 'content-length') {
            responseHeaders[key] = value;
        }
    });

    response.writeHead(upstream.status, responseHeaders);
    response.end(Buffer.from(await upstream.arrayBuffer()));
}

const server = createServer(async (request, response) => {
    try {
        if ((request.url || '').startsWith('/api/')) {
            await proxyApi(request, response);
            return;
        }

        await serveStatic(request, response);
    } catch (error) {
        response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        response.end(error instanceof Error ? error.message : 'Server error');
    }
});

server.listen(PORT, HOST, () => {
    console.log(`Preview proxy listening at http://${HOST}:${PORT}`);
});
