import * as vscode from 'vscode';
import { getAllLocalIps } from '../utils/network';

let panel: vscode.WebviewPanel | null = null;

export function showQrPanel(qrDataUrl: string, connectionUrl: string, port: number): void {
  if (panel) {
    panel.reveal();
  } else {
    panel = vscode.window.createWebviewPanel(
      'sketchcodeQr',
      'SketchCode - Connect Phone',
      vscode.ViewColumn.Beside,
      { enableScripts: true }
    );
    panel.onDidDispose(() => { panel = null; });
  }

  const ips = getAllLocalIps();

  panel.webview.html = getQrHtml(qrDataUrl, connectionUrl, ips, port);
}

export function updateQrPanelStatus(connected: boolean): void {
  if (panel) {
    panel.webview.postMessage({ type: 'statusUpdate', connected });
  }
}

export function disposeQrPanel(): void {
  panel?.dispose();
  panel = null;
}

function getQrHtml(qrDataUrl: string, connectionUrl: string, ips: string[], port: number): string {
  return `<!DOCTYPE html>
<html>
<head>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 20px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
    }
    h2 { margin-bottom: 8px; }
    .qr-container {
      background: white;
      padding: 16px;
      border-radius: 12px;
      margin: 16px 0;
    }
    .qr-container img { display: block; }
    .url-box {
      background: var(--vscode-textBlockQuote-background);
      padding: 8px 12px;
      border-radius: 6px;
      font-family: monospace;
      font-size: 12px;
      word-break: break-all;
      cursor: pointer;
      margin: 8px 0;
      max-width: 400px;
      text-align: center;
    }
    .url-box:hover { opacity: 0.8; }
    .status {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 16px;
      font-size: 14px;
    }
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #f59e0b;
    }
    .dot.connected { background: #22c55e; }
    .instructions {
      margin-top: 20px;
      font-size: 13px;
      opacity: 0.8;
      max-width: 350px;
      text-align: center;
      line-height: 1.5;
    }
    .ips {
      font-size: 11px;
      opacity: 0.6;
      margin-top: 12px;
    }
  </style>
</head>
<body>
  <h2>SketchCode</h2>
  <p>Scan with your phone to connect</p>

  <div class="qr-container">
    <img src="${qrDataUrl}" alt="QR Code" width="300" height="300" />
  </div>

  <div class="url-box" id="urlBox" title="Click to copy">
    ${connectionUrl}
  </div>

  <div class="status">
    <div class="dot" id="statusDot"></div>
    <span id="statusText">Waiting for phone...</span>
  </div>

  <div class="instructions">
    Open the camera app on your phone and scan the QR code, or open the URL above in a mobile browser.
  </div>

  <div class="ips">
    Available IPs: ${ips.join(', ')} | Port: ${port}
  </div>

  <script>
    const vscode = acquireVsCodeApi();

    document.getElementById('urlBox').addEventListener('click', () => {
      navigator.clipboard.writeText('${connectionUrl}');
      document.getElementById('urlBox').textContent = 'Copied!';
      setTimeout(() => {
        document.getElementById('urlBox').textContent = '${connectionUrl}';
      }, 2000);
    });

    window.addEventListener('message', (event) => {
      const msg = event.data;
      if (msg.type === 'statusUpdate') {
        const dot = document.getElementById('statusDot');
        const text = document.getElementById('statusText');
        if (msg.connected) {
          dot.classList.add('connected');
          text.textContent = 'Phone connected!';
        } else {
          dot.classList.remove('connected');
          text.textContent = 'Waiting for phone...';
        }
      }
    });
  </script>
</body>
</html>`;
}
