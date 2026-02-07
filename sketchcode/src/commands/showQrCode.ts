import * as vscode from 'vscode';
import { getCurrentToken } from '../server/auth';
import { generateQrCode } from '../services/qrCode';
import { showQrPanel } from '../webview/qrPanel';
import { getPort } from '../utils/config';

export async function showQrCode(): Promise<void> {
  const token = getCurrentToken();
  if (!token) {
    vscode.window.showWarningMessage('No active SketchCode session. Start a session first.');
    return;
  }

  const port = getPort();
  const { qrDataUrl, connectionUrl } = await generateQrCode(port, token);
  showQrPanel(qrDataUrl, connectionUrl, port);
}
