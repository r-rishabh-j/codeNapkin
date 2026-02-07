import * as vscode from 'vscode';

/**
 * Tree data provider for the SketchCode sidebar.
 * Shows session controls (start/stop) and quick actions.
 */
export class SessionTreeProvider implements vscode.TreeDataProvider<SessionTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<SessionTreeItem | undefined>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private _sessionActive = false;
  private _phoneConnected = false;

  refresh(): void {
    this._onDidChangeTreeData.fire(undefined);
  }

  setSessionActive(active: boolean): void {
    this._sessionActive = active;
    this.refresh();
  }

  setPhoneConnected(connected: boolean): void {
    this._phoneConnected = connected;
    this.refresh();
  }

  getTreeItem(element: SessionTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(): SessionTreeItem[] {
    const items: SessionTreeItem[] = [];

    if (!this._sessionActive) {
      // Session is off — show start button
      items.push(
        new SessionTreeItem(
          'Start Session',
          'Start a SketchCode session',
          'sketchcode.startSession',
          new vscode.ThemeIcon('play'),
        )
      );
    } else {
      // Session is on — show status + controls
      const statusIcon = this._phoneConnected ? 'broadcast' : 'circle-outline';
      const statusLabel = this._phoneConnected ? 'Phone Connected' : 'Waiting for Phone';
      items.push(
        new SessionTreeItem(
          statusLabel,
          this._phoneConnected
            ? 'A phone is connected to this session'
            : 'Scan the QR code with your phone to connect',
          undefined,
          new vscode.ThemeIcon(statusIcon),
        )
      );

      items.push(
        new SessionTreeItem(
          'Show QR Code',
          'Display the QR code to connect your phone',
          'sketchcode.showQrCode',
          new vscode.ThemeIcon('qr-code'),
        )
      );

      items.push(
        new SessionTreeItem(
          'Show Latest Annotation',
          'Show the most recent sketch annotation',
          'sketchcode.showAnnotation',
          new vscode.ThemeIcon('note'),
        )
      );

      items.push(
        new SessionTreeItem(
          'Stop Session',
          'Stop the current SketchCode session',
          'sketchcode.stopSession',
          new vscode.ThemeIcon('debug-stop'),
        )
      );
    }

    return items;
  }
}

class SessionTreeItem extends vscode.TreeItem {
  constructor(
    label: string,
    tooltip: string,
    commandId: string | undefined,
    icon: vscode.ThemeIcon,
  ) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.tooltip = tooltip;
    this.iconPath = icon;
    if (commandId) {
      this.command = {
        command: commandId,
        title: label,
      };
    }
  }
}
