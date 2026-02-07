# SketchCode

Annotate code on your phone with a stylus, speak a voice command, and let Claude Code make the edits. Built for the Snapdragon Multiverse Hackathon at Columbia University.

# Authors
Ayush Bhauwala\
Rishabh Jain\
Suhas Morisetty

## How It Works

```
┌──────────────┐   WebSocket    ┌──────────────────┐   state.json   ┌───────────┐
│  Android App │ ◄────────────► │  VSCode Extension │ ◄────────────► │ MCP Server│
│  (S Pen +    │   code sync    │  (orchestrator)   │   shared file  │ (Claude   │
│   voice)     │   annotations  │                   │   IPC          │  Code)    │
└──────────────┘                └──────────────────┘                └───────────┘
```

1. **Start session** in VSCode → spawns WebSocket server + Claude Code terminal
2. **Scan QR code** on phone → connects over local WiFi
3. **Code syncs live** from editor to phone (debounced 300ms)
4. **Draw with S Pen** on the code, optionally speak a voice command
5. **Tap Send** → annotated screenshot + voice text sent to extension
6. **Claude Code** automatically receives the annotation, sees the sketch visually, reads the voice command, and edits the file

## Project Structure

```
.
├── sketchcode/                  # VSCode Extension + MCP Server
│   ├── src/                     # Extension source (TypeScript)
│   │   ├── commands/            # startSession, stopSession, showQrCode, showAnnotation
│   │   ├── server/              # WebSocket server, HTTP server, auth
│   │   ├── services/            # Claude Code manager, shared state, annotations, code capture
│   │   ├── webview/             # QR panel, annotation panel
│   │   ├── types/               # TypeScript interfaces
│   │   └── utils/               # Config, logger, network utils
│   ├── mcp-server/              # MCP Server (Claude Code integration)
│   │   ├── index.ts             # Server entry — stdio transport, 4 tools
│   │   ├── stateReader.ts       # Reads shared state, PID liveness checks
│   │   └── tools/               # get_pending_annotation, get_current_code, etc.
│   ├── web-client/              # Browser fallback client (HTML/CSS/JS)
│   ├── package.json
│   ├── tsconfig.json            # Extension TypeScript config
│   └── tsconfig.mcp.json        # MCP server TypeScript config
│
├── sketchcode-android/          # Native Android App (Kotlin/Compose)
│   ├── app/src/main/java/com/sketchcode/app/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt     # State management, annotation sending
│   │   ├── network/
│   │   │   └── SketchCodeClient.kt  # OkHttp WebSocket client
│   │   ├── service/
│   │   │   └── VoiceRecorder.kt     # Android SpeechRecognizer wrapper
│   │   └── ui/
│   │       ├── screens/         # ScannerScreen, SketchScreen, MainScreen
│   │       ├── components/      # SketchCanvasView (stylus-only native View)
│   │       └── theme/
│   ├── app/build.gradle.kts
│   └── build.gradle.kts
│
├── PROPOSAL.md
└── README.md
```

## Setup on a New Machine

### Prerequisites

- **Node.js** >= 18
- **npm**
- **VSCode** (with `code` CLI in PATH)
- **Android Studio** (for the phone app)
- **Claude Code CLI** (`npm install -g @anthropic-ai/claude-code` or `brew install claude-code`)
- Both devices on the **same WiFi network**

### 1. Clone and Install

```bash
git clone https://github.com/r-rishabh-j/codeNapkin.git
cd codeNapkin
```

### 2. Build the VSCode Extension

```bash
cd sketchcode
npm install
npm run build
```

This builds both:
- `dist/extension.js` — the VSCode extension (CJS)
- `dist/mcp-server/index.js` — the MCP server (ESM)

### 3. Package and Install the Extension

```bash
npx vsce package --allow-missing-repository
code --install-extension sketchcode-0.1.0.vsix --force
```

Then **reload VSCode** (`Cmd+Shift+P` → "Developer: Reload Window").

### 4. Build the Android App

Open `sketchcode-android/` in Android Studio:

```bash
cd ../sketchcode-android
```

- Open this folder in Android Studio
- Let Gradle sync
- Connect your phone via USB or use wireless debugging
- Run the app (`Shift+F10`)

**Note**: The app needs `INTERNET`, `RECORD_AUDIO`, and `CAMERA` permissions. It will prompt on first launch.

### 5. Run It

1. In VSCode: `Cmd+Shift+P` → **"SketchCode: Start Session"**
   - This starts the WebSocket server, opens the QR panel, and launches a Claude Code terminal
2. On your phone: Open the SketchCode app → scan the QR code
3. Your current editor file will appear on the phone
4. Draw annotations with the **S Pen** (finger scrolls the code)
5. Tap 🎤 to add a voice command
6. Tap **Send** → Claude Code will see your sketch and make edits

### 6. (Optional) Manual MCP Setup

If you want to use Claude Code separately without the extension spawning it, create a `.mcp.json` in your project root:

```json
{
  "mcpServers": {
    "sketchcode": {
      "command": "node",
      "args": ["<full-path-to>/sketchcode/dist/mcp-server/index.js"]
    }
  }
}
```

Then run `claude` in that directory. Use the `get_pending_annotation` tool to fetch sketches.

## MCP Tools

| Tool | Description |
|------|-------------|
| `get_pending_annotation` | Returns the latest sketch (PNG image) + voice transcription + filename + code |
| `get_current_code` | Returns the current file open in the editor |
| `send_code_to_phone` | Pushes updated code to the phone display |
| `get_session_status` | Returns session state (active, phone connected, etc.) |

## Architecture Notes

- **IPC**: Extension ↔ MCP Server communicate via a shared JSON file (`~/.sketchcode/state.json`) with atomic writes (write `.tmp`, then `rename`)
- **Liveness**: State file stores the extension's PID. MCP server checks PID is alive before returning data.
- **Auth**: 32-byte random token per session, embedded in QR code URL, validated on WebSocket handshake
- **Live sync**: Editor changes are debounced (300ms) and pushed to phone via WebSocket `code_update` messages
- **Stylus-only drawing**: `SketchCanvasView.onTouchEvent` checks `MotionEvent.TOOL_TYPE_STYLUS` — finger input passes through to ScrollView
- **Voice**: Android's built-in `SpeechRecognizer` (Google Speech-to-Text)
