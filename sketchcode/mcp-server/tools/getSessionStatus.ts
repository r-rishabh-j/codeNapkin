import { readState } from '../stateReader.js';

export function getSessionStatus(): {
  content: Array<{ type: string; text: string }>;
} {
  const state = readState();
  if (!state) {
    return {
      content: [{ type: 'text', text: 'No SketchCode state file found. No session has been started.' }],
    };
  }

  const status = {
    sessionActive: state.sessionActive,
    phoneConnected: state.phoneConnected,
    hasPendingAnnotation: state.pendingAnnotation !== null,
    currentFile: state.currentCode?.filename || 'none',
    lastUpdated: new Date(state.lastUpdated).toISOString(),
  };

  return {
    content: [{
      type: 'text',
      text: `## SketchCode Session Status
- **Session Active**: ${status.sessionActive}
- **Phone Connected**: ${status.phoneConnected}
- **Pending Annotation**: ${status.hasPendingAnnotation}
- **Current File**: ${status.currentFile}
- **Last Updated**: ${status.lastUpdated}`,
    }],
  };
}
