import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';
import { log } from '../utils/logger';

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

let server: http.Server | null = null;

/**
 * Create and start the HTTP server.
 * Serves static files from web-client/ and a /health endpoint.
 */
export function startHttpServer(port: number, extensionPath: string): http.Server {
  const webClientDir = path.join(extensionPath, 'web-client');

  server = http.createServer((req, res) => {
    const url = req.url || '/';

    // Health check
    if (url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'ok', timestamp: Date.now() }));
      return;
    }

    // Serve web client static files
    if (url.startsWith('/web-client/') || url === '/web-client') {
      let filePath = url.replace('/web-client', '');
      if (filePath === '' || filePath === '/') filePath = '/index.html';

      const fullPath = path.join(webClientDir, filePath);

      // Prevent path traversal
      if (!fullPath.startsWith(webClientDir)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
      }

      if (!fs.existsSync(fullPath)) {
        res.writeHead(404);
        res.end('Not found');
        return;
      }

      const ext = path.extname(fullPath);
      const mime = MIME_TYPES[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': mime });
      fs.createReadStream(fullPath).pipe(res);
      return;
    }

    res.writeHead(404);
    res.end('Not found');
  });

  server.listen(port, '0.0.0.0', () => {
    log(`HTTP server listening on port ${port}`);
  });

  return server;
}

export function stopHttpServer(): void {
  if (server) {
    server.close();
    server = null;
    log('HTTP server stopped');
  }
}

export function getHttpServer(): http.Server | null {
  return server;
}
