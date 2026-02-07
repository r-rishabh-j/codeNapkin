import * as vscode from 'vscode';
import { clearToken } from '../server/auth';
import { stopHttpServer } from '../server/httpServer';
import { annotationStore } from '../services/annotationStore';
import { cleanupState, stopCommandPolling } from '../services/sharedState';
import { disposeQrPanel } from '../webview/qrPanel';
import { disposeAnnotationPanel } from '../webview/annotationPanel';
import { log, logError } from '../utils/logger';
import { getWSServer, clearSession } from './startSession';

export function stopSession(): void {
  const wsServer = getWSServer();
  if (!wsServer) {
    // No active session — still try to clean up state file in case it's stale
    try { cleanupState(); } catch { /* ignore */ }
    return;
  }

  // Each step is wrapped so one failure doesn't prevent the rest from running

  try { wsServer.stop(); } catch (e) { logError('Error stopping WS server', e); }
  try { stopHttpServer(); } catch (e) { logError('Error stopping HTTP server', e); }
  try { stopCommandPolling(); } catch (e) { logError('Error stopping command polling', e); }
  try { cleanupState(); } catch (e) { logError('Error cleaning up state', e); }
  try { clearToken(); } catch (e) { logError('Error clearing token', e); }
  try { annotationStore.clear(); } catch (e) { logError('Error clearing annotations', e); }
  try { disposeQrPanel(); } catch (e) { logError('Error disposing QR panel', e); }
  try { disposeAnnotationPanel(); } catch (e) { logError('Error disposing annotation panel', e); }
  try { clearSession(); } catch (e) { logError('Error clearing session refs', e); }

  log('Session stopped');
  vscode.window.showInformationMessage('SketchCode session stopped');
}
