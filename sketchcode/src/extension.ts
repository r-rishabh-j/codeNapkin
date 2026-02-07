import * as vscode from 'vscode';
import { startSession } from './commands/startSession';
import { stopSession } from './commands/stopSession';
import { showQrCode } from './commands/showQrCode';
import { showAnnotation } from './commands/showAnnotation';
import { log, disposeLogger } from './utils/logger';
import { initSharedState, cleanupStaleState } from './services/sharedState';
import { getStateFilePath } from './utils/config';
import { SessionTreeProvider } from './views/sessionTreeProvider';

let sessionTreeProvider: SessionTreeProvider | null = null;

export function getSessionTreeProvider(): SessionTreeProvider | null {
  return sessionTreeProvider;
}

export function activate(context: vscode.ExtensionContext) {
  log('SketchCode extension activated');

  // Safety: clean up stale state from a crashed previous session
  initSharedState(getStateFilePath());
  cleanupStaleState();

  // Register sidebar tree view
  sessionTreeProvider = new SessionTreeProvider();
  const treeView = vscode.window.createTreeView('sketchcode.sessionView', {
    treeDataProvider: sessionTreeProvider,
  });
  context.subscriptions.push(treeView);

  // Set initial context for menu visibility
  vscode.commands.executeCommand('setContext', 'sketchcode.sessionActive', false);

  context.subscriptions.push(
    vscode.commands.registerCommand('sketchcode.startSession', async () => {
      await startSession(context.extensionPath);
      vscode.commands.executeCommand('setContext', 'sketchcode.sessionActive', true);
      sessionTreeProvider?.setSessionActive(true);
    }),
    vscode.commands.registerCommand('sketchcode.stopSession', () => {
      stopSession();
      vscode.commands.executeCommand('setContext', 'sketchcode.sessionActive', false);
      sessionTreeProvider?.setSessionActive(false);
      sessionTreeProvider?.setPhoneConnected(false);
    }),
    vscode.commands.registerCommand('sketchcode.showQrCode', () =>
      showQrCode()
    ),
    vscode.commands.registerCommand('sketchcode.showAnnotation', () =>
      showAnnotation()
    )
  );
}

export function deactivate() {
  stopSession();
  disposeLogger();
}
