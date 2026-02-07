import * as vscode from 'vscode';

export interface CodeSnapshot {
  filename: string;
  code: string;
  language: string;
  lineCount: number;
  cursorLine: number;
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
