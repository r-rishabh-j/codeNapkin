import * as vscode from 'vscode';
import { startSession } from './commands/startSession';
import { stopSession } from './commands/stopSession';
import { showQrCode } from './commands/showQrCode';
import { showAnnotation } from './commands/showAnnotation';
import { log, disposeLogger } from './utils/logger';
import { initSharedState, cleanupStaleState } from './services/sharedState';
import { getStateFilePath } from './utils/config';

export function activate(context: vscode.ExtensionContext) {
  log('SketchCode extension activated');

  // Safety: clean up stale state from a crashed previous session
  initSharedState(getStateFilePath());
  cleanupStaleState();

  context.subscriptions.push(
    vscode.commands.registerCommand('sketchcode.startSession', () =>
      startSession(context.extensionPath)
    ),
    vscode.commands.registerCommand('sketchcode.stopSession', () =>
      stopSession()
    ),
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
