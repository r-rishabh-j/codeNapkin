import * as vscode from 'vscode';
import { v4 as uuidv4 } from 'uuid';
import { generateToken } from '../server/auth';
import { startHttpServer } from '../server/httpServer';
import { createWSServer, SketchCodeWSServer } from '../server/websocketServer';
import { generateQrCode } from '../services/qrCode';
import { captureActiveEditor, getOpenFiles } from '../services/codeCapture';
import { annotationStore } from '../services/annotationStore';
import {
  initSharedState,
  writeState,
  readState,
  getDefaultState,
  startCommandPolling,
} from '../services/sharedState';
import { startClaudeCode, sendToClaudeCode, stopClaudeCode } from '../services/claudeCode';
import { showQrPanel, updateQrPanelStatus } from '../webview/qrPanel';
import { showAnnotationPanel } from '../webview/annotationPanel';
import { getPort, getStateFilePath } from '../utils/config';
import { log } from '../utils/logger';
import { AnnotationMessage, CodeUpdateMessage, OpenFilesMessage, FileSelectMessage } from '../types';

let wsServer: SketchCodeWSServer | null = null;
let editorChangeDisposable: vscode.Disposable | null = null;
let activeEditorDisposable: vscode.Disposable | null = null;
let tabChangeDisposable: vscode.Disposable | null = null;
let debounceTimer: NodeJS.Timeout | null = null;
let sessionId: string | null = null;
let currentCodeState: { filename: string; code: string; language: string; lineCount: number } | null = null;

export async function startSession(extensionPath: string): Promise<void> {
  if (wsServer) {
    vscode.window.showWarningMessage('SketchCode session already active');
    return;
  }

  const port = getPort();
  const token = generateToken();
  sessionId = uuidv4();

  // Initialize shared state
  const stateFilePath = getStateFilePath();
  initSharedState(stateFilePath);
  const state = getDefaultState(sessionId);
  state.sessionActive = true;
  writeState(state);

  // Start HTTP server
  const httpServer = startHttpServer(port, extensionPath);

  // Start WebSocket server
  wsServer = createWSServer();
  wsServer.start(httpServer);

  // Start Claude Code as a child terminal with MCP config
  startClaudeCode(extensionPath);

  // Handle phone connection events
  wsServer.on('phone_connected', () => {
    log('Phone connected');
    updateQrPanelStatus(true);

    // Update shared state
    const st = getDefaultState(sessionId!);
    st.sessionActive = true;
    st.phoneConnected = true;
    st.currentCode = currentCodeState;
    writeState(st);

    // Send open files list + current code immediately
    sendOpenFilesList();
    sendCodeUpdate();
  });

  // Handle file selection from phone
  wsServer.on('file_select', (msg: FileSelectMessage) => {
    log(`Phone requested file: ${msg.payload.fullPath}`);
    // Open the file in VSCode and make it active — this triggers onDidChangeActiveTextEditor
    vscode.workspace.openTextDocument(msg.payload.fullPath).then((doc) => {
      vscode.window.showTextDocument(doc, { preview: false });
    });
  });

  wsServer.on('phone_disconnected', () => {
    log('Phone disconnected');
    updateQrPanelStatus(false);

    const st = getDefaultState(sessionId!);
    st.sessionActive = true;
    st.phoneConnected = false;
    st.currentCode = currentCodeState;
    // Preserve pending annotations — Claude may still be processing them
    const existing = readState();
    st.pendingAnnotations = existing?.pendingAnnotations || [];
    writeState(st);
  });

  // Handle annotations from phone
  wsServer.on('annotation', (msg: AnnotationMessage) => {
    // Use the filename sent by the phone, fall back to current active file
    const annotationFilename = msg.payload.filename || currentCodeState?.filename || 'unknown';
    const snapshot = {
      filename: annotationFilename,
      code: currentCodeState?.code || '',
      language: currentCodeState?.language || 'plaintext',
      lineCount: currentCodeState?.lineCount || 0,
    };
    const annotation = annotationStore.add({
      sketchImageBase64: msg.payload.sketchImageBase64,
      voiceTranscription: msg.payload.voiceTranscription,
      codeSnapshot: snapshot,
    });

    // Append to pending annotations array (don't overwrite previous ones)
    const existing = readState();
    const st = getDefaultState(sessionId!);
    st.sessionActive = true;
    st.phoneConnected = wsServer!.isPhoneConnected();
    st.currentCode = currentCodeState;
    st.pendingAnnotations = existing?.pendingAnnotations || [];
    st.pendingAnnotations.push({
      id: annotation.id,
      sketchImageBase64: annotation.sketchImageBase64,
      voiceTranscription: annotation.voiceTranscription,
      codeFilename: annotationFilename,
      codeContent: annotation.codeSnapshot.code,
      timestamp: annotation.timestamp,
    });
    writeState(st);

    // Show annotation in VSCode
    showAnnotationPanel(annotation);

    // Type prompt into Claude terminal — user presses enter when ready
    const voiceHint = annotation.voiceTranscription
      ? ` The user said: "${annotation.voiceTranscription}".`
      : '';
    sendToClaudeCode(
      `New annotation received for "${annotationFilename}".${voiceHint} ` +
      `Call get_pending_annotation to see the annotated screenshot(s) and make the requested changes.`
    );

    vscode.window.showInformationMessage('SketchCode: Annotation ready — press Enter in Claude terminal to execute');
  });

  // Listen for editor changes (live sync)
  editorChangeDisposable = vscode.workspace.onDidChangeTextDocument(() => {
    debouncedCodeUpdate();
  });

  activeEditorDisposable = vscode.window.onDidChangeActiveTextEditor(() => {
    sendOpenFilesList();
    sendCodeUpdate();
  });

  // Listen for tab open/close to update the file list on phone
  tabChangeDisposable = vscode.window.tabGroups.onDidChangeTabs(() => {
    sendOpenFilesList();
  });

  // Start MCP command polling
  startCommandPolling(async (cmd) => {
    if (cmd.command === 'send_code_to_phone' || cmd.command === 'refresh_code') {
      sendCodeUpdate();
      return 'Code sent to phone';
    }
    return undefined;
  });

  // Generate and show QR code
  const { qrDataUrl, connectionUrl } = await generateQrCode(port, token);
  showQrPanel(qrDataUrl, connectionUrl, port);

  // Capture initial code
  sendCodeUpdate();

  log(`Session started (id: ${sessionId}, port: ${port})`);
  vscode.window.showInformationMessage(`SketchCode session started on port ${port}`);
}

function sendCodeUpdate(): void {
  const snapshot = captureActiveEditor();
  if (!snapshot || !wsServer) return;

  currentCodeState = {
    filename: snapshot.filename,
    code: snapshot.code,
    language: snapshot.language,
    lineCount: snapshot.lineCount,
  };

  const message: CodeUpdateMessage = {
    type: 'code_update',
    payload: {
      filename: snapshot.filename,
      code: snapshot.code,
      language: snapshot.language,
      cursorLine: snapshot.cursorLine,
      lineCount: snapshot.lineCount,
      timestamp: Date.now(),
    },
  };

  wsServer.sendToPhone(message);

  // Update shared state (less frequently - reuse debounce)
  const stateFilePath = getStateFilePath();
  if (stateFilePath) {
    const st = getDefaultState(sessionId!);
    st.sessionActive = true;
    st.phoneConnected = wsServer.isPhoneConnected();
    st.currentCode = currentCodeState;
    // Preserve pending annotations from state (don't rebuild from store — store is append-only)
    const existingState = readState();
    st.pendingAnnotations = existingState?.pendingAnnotations || [];
    writeState(st);
  }
}

function sendOpenFilesList(): void {
  if (!wsServer) return;
  const { files, activeFile } = getOpenFiles();
  log(`sendOpenFilesList: ${files.length} files, active: ${activeFile}`);
  for (const f of files) {
    log(`  tab: ${f.filename} (${f.fullPath})`);
  }
  const message: OpenFilesMessage = {
    type: 'open_files',
    payload: { files, activeFile },
  };
  wsServer.sendToPhone(message);
}

function debouncedCodeUpdate(): void {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => sendCodeUpdate(), 300);
}

export function getWSServer(): SketchCodeWSServer | null {
  return wsServer;
}

export function clearSession(): void {
  stopClaudeCode();
  wsServer = null;
  sessionId = null;
  currentCodeState = null;
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  if (editorChangeDisposable) {
    editorChangeDisposable.dispose();
    editorChangeDisposable = null;
  }
  if (activeEditorDisposable) {
    activeEditorDisposable.dispose();
    activeEditorDisposable = null;
  }
  if (tabChangeDisposable) {
    tabChangeDisposable.dispose();
    tabChangeDisposable = null;
  }
}
