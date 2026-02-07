import * as vscode from 'vscode';

export interface CodeSnapshot {
  filename: string;
  code: string;
  language: string;
  lineCount: number;
  cursorLine: number;
}

export interface OpenFileInfo {
  filename: string;
  fullPath: string;
}

/** Capture the current active editor's content */
export function captureActiveEditor(): CodeSnapshot | null {
  const editor = vscode.window.activeTextEditor;
  if (!editor) return null;

  const doc = editor.document;
  return {
    filename: doc.fileName.split('/').pop() || doc.fileName,
    code: doc.getText(),
    language: doc.languageId,
    lineCount: doc.lineCount,
    cursorLine: editor.selection.active.line + 1,
  };
}

/** Get all open editor tabs (visible text files) */
export function getOpenFiles(): { files: OpenFileInfo[]; activeFile: string } {
  const files: OpenFileInfo[] = [];
  const seen = new Set<string>();

  for (const group of vscode.window.tabGroups.all) {
    for (const tab of group.tabs) {
      const input = tab.input;
      if (input instanceof vscode.TabInputText) {
        const fsPath = input.uri.fsPath;
        if (!seen.has(fsPath)) {
          seen.add(fsPath);
          files.push({
            filename: fsPath.split('/').pop() || fsPath,
            fullPath: fsPath,
          });
        }
      }
    }
  }

  const activeFile = vscode.window.activeTextEditor?.document.fileName.split('/').pop() || '';
  return { files, activeFile };
}
