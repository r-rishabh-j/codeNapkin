import * as vscode from 'vscode';
import * as path from 'path';
import * as os from 'os';

export function getPort(): number {
  return vscode.workspace.getConfiguration('sketchcode').get<number>('port', 9876);
}

export function getStateFilePath(): string {
  const custom = vscode.workspace.getConfiguration('sketchcode').get<string>('stateFilePath', '');
  if (custom) return custom;
  return path.join(os.homedir(), '.sketchcode', 'state.json');
}
