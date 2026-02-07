import * as vscode from 'vscode';
import { annotationStore } from '../services/annotationStore';
import { showAnnotationPanel } from '../webview/annotationPanel';

export function showAnnotation(): void {
  const annotation = annotationStore.getLatest();
  if (!annotation) {
    vscode.window.showInformationMessage('No annotations received yet');
    return;
  }
  showAnnotationPanel(annotation);
}
