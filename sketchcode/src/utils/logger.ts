import * as vscode from 'vscode';

let outputChannel: vscode.OutputChannel | null = null;

export function getLogger(): vscode.OutputChannel {
  if (!outputChannel) {
    outputChannel = vscode.window.createOutputChannel('SketchCode');
  }
  return outputChannel;
}

export function log(message: string): void {
  getLogger().appendLine(`[${new Date().toISOString()}] ${message}`);
}

export function logError(message: string, error?: unknown): void {
  const errMsg = error instanceof Error ? error.message : String(error ?? '');
  getLogger().appendLine(`[${new Date().toISOString()}] ERROR: ${message} ${errMsg}`);
}

export function disposeLogger(): void {
  outputChannel?.dispose();
  outputChannel = null;
}
