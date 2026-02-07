# SketchCode: Multi-Device AI-Powered Code Shaping

> **Hackathon:** Snapdragon Multiverse Hackathon – Columbia University
> **Date:** February 6-7, 2026
> **Track:** Track 2 - Conversational AI Companion
> **Platforms:** Compute (Copilot+ PC) + Mobile (Samsung Galaxy S25)

---

## Executive Summary

SketchCode reimagines code editing through free-form sketching across multiple Snapdragon devices. Inspired by the CHI 2025 research paper "Code Shaping: Iterative Code Editing with Free-form AI-Interpreted Sketching," our system enables programmers to draw annotations, arrows, and pseudocode on a Samsung Galaxy S25, which are interpreted by AI to generate real-time code edits on a Copilot+ PC.

---

## Problem Statement

Traditional code editing is keyboard-centric, forcing programmers to:
- Translate visual ideas (flowcharts, diagrams) into text manually
- Context-switch between sketching/planning and actual coding
- Lose insights during the translation from visual concepts to code

**The Gap:** Sketching and code editing remain separate activities, despite research showing programmers frequently sketch to plan code changes.

---

## Our Solution

A **multi-device code shaping platform** where:

1. **PC serves as the code hub** - Running the editor, executing code, orchestrating AI
2. **Phone serves as the sketch canvas** - Natural pen/touch input for annotations
3. **AI bridges the gap** - Interprets sketches and generates precise code edits

### Key Innovation

The original research prototype ran on a single tablet. We create a **true multi-device experience** that showcases Snapdragon's cross-device ecosystem.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SNAPDRAGON PC (Copilot+ X Series)                │
│                                                                     │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │   Code Editor   │  │  AI Interpreter  │  │  Code Executor    │  │
│  │   (Monaco)      │◄─┤  (Local LLM +    │  │  (Python Runtime) │  │
│  │                 │  │   Cloud Fallback)│  │                   │  │
│  └────────▲────────┘  └────────▲─────────┘  └───────────────────┘  │
│           │                    │                                    │
│           └────────────────────┼────────────────────────────────────┤
│                                │                                    │
│                         REST API Server (:8080)                     │
│                    GET /code  POST /sketch  POST /decision          │
└────────────────────────────────┼────────────────────────────────────┘
                                 │
                         Local WiFi Network
                                 │
┌────────────────────────────────┼────────────────────────────────────┐
│                    SAMSUNG GALAXY S25 (Snapdragon 8 Elite)          │
│                                │                                    │
│  ┌─────────────────┐  ┌───────┴──────────┐  ┌───────────────────┐  │
│  │  Sketch Canvas  │  │  On-Device AI    │  │   Send Button     │  │
│  │  (Touch/Stylus) │──┤  (NPU-accelerated│  │   [  Send  ]      │  │
│  │                 │  │   HWR)           │  │                   │  │
│  └─────────────────┘  └──────────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Interaction Model: Draw → Send → Get Code

Unlike real-time streaming approaches, SketchCode uses a **batch-based interaction**:

```
┌─────────────────────────────────────────────────────────────────┐
│  PHONE: Drawing Phase                                           │
│                                                                 │
│  1. User sees code snapshot on phone screen                     │
│  2. User draws annotations (arrows, circles, text)              │
│  3. User taps "Send" button when done                           │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  SEND: Capture & Transmit                                       │
│                                                                 │
│  • Capture sketch as PNG image                                  │
│  • Run on-device HWR for any handwritten text                   │
│  • Send to PC: {image, recognized_text, code_snapshot}          │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  PC: AI Processing                                              │
│                                                                 │
│  • Combine code + sketch overlay into single image              │
│  • Send to AI: "Interpret this sketch and generate code edits"  │
│  • Receive: {action, target_lines, new_code}                    │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  OUTPUT: Review & Apply                                         │
│                                                                 │
│  • PC shows diff view with proposed changes                     │
│  • Phone shows simplified accept/reject UI                      │
│  • User taps ✓ to apply or ✗ to discard                         │
│  • Canvas clears, ready for next sketch                         │
└─────────────────────────────────────────────────────────────────┘
```

### Why Batch Over Streaming?

| Aspect | Batch (Our Approach) | Real-time Streaming |
|--------|---------------------|---------------------|
| **Complexity** | Simple request-response | Complex state sync |
| **Reliability** | Single network call | Continuous connection |
| **User Control** | Review before sending | Partial sketches interpreted |
| **AI Quality** | Complete context | Incomplete annotations |
| **Battery** | Low (on-demand) | High (always transmitting) |

---

## Core Features

### P0 - Must Have (MVP)

| Feature | Description | Implementation |
|---------|-------------|----------------|
| **Code Snapshot View** | Phone displays current code from PC | REST API: `GET /code` returns code + screenshot |
| **Sketch Canvas** | Draw annotations on top of code snapshot | Android Canvas with touch/stylus support |
| **Send Button** | Capture sketch + send to PC for processing | REST API: `POST /sketch` with image payload |
| **AI Interpretation** | Interpret sketch image → generate code edits | Claude API / GPT-4o with vision |
| **Code Diff View** | Show proposed changes on PC | Monaco Diff Editor |
| **Accept/Reject UI** | Simple buttons to apply or discard changes | Phone: two-button UI; PC: keyboard shortcuts |

### P1 - Should Have

| Feature | Description | Implementation |
|---------|-------------|----------------|
| **On-Device HWR** | Pre-process handwritten text before sending | Qualcomm Neural Processing SDK / ML Kit |
| **Interpretation Preview** | Show AI's understanding before applying | Display on phone after POST response |
| **Undo/Redo** | Revert applied changes | PC maintains edit history stack |

### P2 - Nice to Have

| Feature | Description | Implementation |
|---------|-------------|----------------|
| **Voice Commands** | "Send", "Accept", "Reject" | Android Speech Recognition API |
| **Multi-file Support** | Select which file to annotate | File picker on phone |
| **Sketch History** | Review previous sketches and their results | Local storage on phone |

---

## Technical Stack

### PC Application (Electron)

```
├── Frontend
│   ├── React 18
│   ├── Monaco Editor (VS Code's editor component)
│   ├── TailwindCSS
│   └── Diff view component
│
├── Backend (Express.js)
│   ├── REST API endpoints (/code, /sketch, /decision)
│   ├── Python subprocess for code execution
│   ├── Screenshot capture (html2canvas)
│   └── File system operations
│
└── AI Integration
    ├── Claude API (claude-3-5-sonnet) with vision
    ├── Or: GPT-4o with vision capability
    └── Prompt: Code screenshot + sketch overlay → Edit instructions
```

### Android Application (Kotlin)

```
├── UI Layer
│   ├── Jetpack Compose
│   ├── Custom SketchCanvas composable
│   ├── Code snapshot ImageView
│   └── Material 3 Design (Send/Accept/Reject buttons)
│
├── Drawing Engine
│   ├── Pressure-sensitive stroke capture
│   ├── Multiple pen colors and sizes
│   ├── Eraser tool
│   └── Clear canvas button
│
├── On-Device AI (Optional Enhancement)
│   ├── ML Kit Digital Ink Recognition
│   └── Pre-process handwritten text before sending
│
└── Networking
    ├── Retrofit + OkHttp for REST API calls
    ├── mDNS/NSD for PC discovery on local network
    └── Manual IP entry fallback
```

### Communication Protocol (REST API)

#### 1. Get Current Code
```http
GET /code
```
**Response:**
```json
{
  "filename": "pipeline.py",
  "code": "import pandas as pd\n\nclass DataProcessor:\n    ...",
  "screenshot": "data:image/png;base64,iVBORw0KGgo...",
  "line_count": 78
}
```

#### 2. Send Sketch for Interpretation
```http
POST /sketch
Content-Type: multipart/form-data
```
**Request:**
```
sketch_image: <PNG file of code + annotations overlay>
recognized_text: "def plot features"  (optional, from on-device HWR)
```

**Response:**
```json
{
  "status": "success",
  "interpretation": "Create a new function 'plot_features' that visualizes the data using a bar chart",
  "action": "insert",
  "target_line": 25,
  "new_code": "def plot_features(self):\n    self.dataframe.plot(kind='bar')\n    plt.xlabel('Index')\n    plt.ylabel('Value')\n    plt.title('Feature Values')\n    plt.show()",
  "affected_lines": [25, 26, 27, 28, 29, 30],
  "confidence": 0.89,
  "diff_preview": "..."
}
```

#### 3. Apply or Reject Changes
```http
POST /decision
Content-Type: application/json
```
**Request:**
```json
{
  "action": "accept"  // or "reject"
}
```

**Response:**
```json
{
  "status": "applied",
  "new_code_snapshot": "data:image/png;base64,..."
}
```

---

## AI Prompt Engineering

### Main Interpretation Prompt

```
You are a code editing assistant that interprets sketches overlaid on code.

## Input
- A screenshot showing code with hand-drawn annotations
- The raw code text with line numbers
- Previously recognized handwritten text (if any)

## Your Task
Analyze the sketches and determine what code edits the user intends.

## Sketch Interpretation Guide
- **Arrows (→)**: Indicate flow, reference, or "insert here"
- **Circles/Boxes**: Highlight code to reference or modify
- **Strikethrough**: Delete this code
- **Checkmark (✓)**: Keep/approve this code
- **Cross (✗)**: Remove/reject this code
- **Handwritten text**: Instructions or code to insert
- **Diagrams**: Desired output structure (e.g., chart, flowchart)

## Output Format
{
  "interpretation": "Brief description of intended edit",
  "action": "insert" | "replace" | "delete" | "wrap" | "move",
  "target_lines": [start, end],
  "new_code": "// Generated code here",
  "confidence": 0.0-1.0
}
```

---

## Development Timeline

### Day 1: Friday, February 6 (11:00 AM - Midnight)

| Time | Duration | Task | Owner |
|------|----------|------|-------|
| 11:00 AM | 1h | Team setup, device configuration, repo init | All |
| 12:00 PM | 1h | Lunch + Architecture review | All |
| 1:00 PM | 2h | PC: Monaco Editor setup + Express server scaffold | Dev 1-2 |
| 1:00 PM | 2h | Android: Sketch canvas + basic UI | Dev 3-4 |
| 3:00 PM | 2h | PC: `GET /code` endpoint + screenshot capture | Dev 1-2 |
| 3:00 PM | 2h | Android: Fetch code snapshot + display | Dev 3-4 |
| 5:00 PM | 1h | Integration: Phone displays PC code | All |
| 6:00 PM | 1h | Dinner break | All |
| 7:00 PM | 2h | PC: `POST /sketch` + Claude API integration | Dev 1-2 |
| 7:00 PM | 2h | Android: Capture sketch + send to PC | Dev 3-4 |
| 9:00 PM | 1h | Integration: Full send flow working | All |
| 10:00 PM | 2h | PC: Diff view + `POST /decision` endpoint | Dev 1-2 |
| 10:00 PM | 2h | Android: Accept/Reject buttons + refresh | Dev 3-4 |
| 12:00 AM | -- | **Day 1 checkpoint: Complete flow working** | All |

### Day 2: Saturday, February 7 (8:00 AM - 7:00 PM)

| Time | Duration | Task | Owner |
|------|----------|------|-------|
| 8:00 AM | 1h | Breakfast + Day 1 bug triage | All |
| 9:00 AM | 2h | PC: Improve prompt engineering + edge cases | Dev 1-2 |
| 9:00 AM | 2h | Android: On-device HWR (ML Kit) | Dev 3-4 |
| 11:00 AM | 2h | PC: Code highlighting + better diff UI | Dev 1-2 |
| 11:00 AM | 2h | Android: Drawing tools (colors, eraser, undo) | Dev 3-4 |
| 1:00 PM | 1h | Lunch | All |
| 2:00 PM | 2h | UI polish, error handling, loading states | All |
| 4:00 PM | 1h | Demo scenario practice runs | All |
| 5:00 PM | 1h | Final bug fixes + edge cases | All |
| 6:00 PM | 1h | Presentation preparation | All |
| 7:00 PM | -- | **Submission deadline** | All |

---

## Demo Scenario Script

### Setup (30 seconds)
1. Show Copilot+ PC with code editor open (Python data processing script)
2. Open SketchCode app on Galaxy S25 → code snapshot loads automatically
3. "The phone shows my code. I can draw on top of it to make changes."

### Demo Flow (2.5 minutes)

**Scene 1: Basic Sketch-to-Code (60s)**
> "I have a data processing pipeline but need to add a visualization function."

1. On phone: Circle the `data` variable definition with finger/stylus
2. Draw an arrow pointing to empty space below the class
3. Write "def plot" with stylus near the arrow
4. Tap **[Send]** button
5. *Phone shows loading spinner (2-3 seconds)*
6. Phone displays: "AI Interpretation: Create plot_features() function"
7. PC shows diff view with green highlighted new code
8. Tap **[Accept ✓]** on phone → code applied on PC
9. Tap **[Clear]** to reset canvas

**Scene 2: Iterative Refinement (45s)**
> "The chart needs min-max scaling. Let me refine it."

1. Run code on PC → chart appears in output panel
2. Phone refreshes with new code snapshot (including the new function)
3. On phone: Draw on top of the plot function
4. Write "add min-max scaling" with an arrow
5. Tap **[Send]** → AI interprets
6. Phone shows: "Add normalization before plotting"
7. Tap **[Accept ✓]** → code updates on PC

**Scene 3: Reject and Retry (30s)**
> "What if the AI gets it wrong? I can reject and try again."

1. Draw a vague annotation
2. Tap **[Send]** → AI misinterprets
3. Tap **[Reject ✗]** → changes discarded
4. Clear canvas, draw clearer annotation
5. Tap **[Send]** → correct interpretation this time
6. Tap **[Accept ✓]**

**Scene 4: Multi-Device Summary (15s)**
> "PC handles the heavy lifting - code editing and AI calls. Phone is just for natural input. Simple, reliable, powerful."

1. Show final working code on PC
2. Show clean phone interface
3. "That's SketchCode - draw your intentions, get working code."

---

## Judging Criteria Alignment

| Criteria | How SketchCode Excels |
|----------|----------------------|
| **Innovation** | Implements cutting-edge CHI 2025 research; novel multi-device interaction paradigm |
| **Multi-device Integration** | True PC + phone collaboration, not just a ported app |
| **Snapdragon Showcase** | NPU for on-device HWR, local LLM inference, low-latency edge AI |
| **Technical Complexity** | AST manipulation, real-time sync, gesture recognition, diff algorithms |
| **Demo Impact** | Visually compelling - sketching creates code in real-time |
| **Practical Value** | Addresses real developer pain points; extensible to other IDEs |

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| AI hallucinations in code gen | High | Medium | Always show diff for user approval; never auto-apply |
| Network discovery fails | Medium | Medium | Manual IP entry fallback; pre-configure for demo |
| Sketch image quality issues | Medium | Low | High-res capture; test various sketch styles |
| API latency (2-5s for AI) | High | Low | Show loading spinner; acceptable for batch flow |
| Time overrun on features | High | Medium | Strict P0/P1/P2 prioritization; cut P2 if needed |
| Device compatibility | Low | High | Test on provided hardware early; have backup devices |

---

## Team Roles (Suggested for 3-5 members)

| Role | Responsibilities |
|------|------------------|
| **PC Lead** | Electron app, Monaco editor, WebSocket server |
| **Android Lead** | Kotlin app, sketch canvas, gesture recognition |
| **AI Engineer** | Prompt engineering, interpretation pipeline, local LLM setup |
| **Integration** | Protocol design, debugging sync issues, testing |
| **Demo/Presenter** | Demo script, presentation slides, video recording |

---

## Resources & References

### Research Paper
- Yen, R., Zhao, J., & Vogel, D. (2025). *Code Shaping: Iterative Code Editing with Free-form AI-Interpreted Sketching*. CHI '25.
- GitHub: https://github.com/CodeShaping/code-shaping

### Snapdragon Resources
- [Qualcomm AI Engine Direct SDK](https://developer.qualcomm.com/software/qualcomm-ai-engine-direct-sdk)
- [Qualcomm Neural Processing SDK](https://developer.qualcomm.com/software/qualcomm-neural-processing-sdk)
- [Snapdragon X Series Developer Guide](https://developer.qualcomm.com/hardware/snapdragon-x-series)

### Libraries
- [Monaco Editor](https://microsoft.github.io/monaco-editor/)
- [$1 Unistroke Recognizer](http://depts.washington.edu/acelab/proj/dollar/index.html)
- [ML Kit Handwriting Recognition](https://developers.google.com/ml-kit/vision/digital-ink-recognition)

---

## Appendix: Sample Code Snippets

### REST API Server (PC - TypeScript/Express)

```typescript
import express from 'express';
import multer from 'multer';
import Anthropic from '@anthropic-ai/sdk';

const app = express();
const upload = multer({ storage: multer.memoryStorage() });
const anthropic = new Anthropic();

let currentCode = '';
let pendingChanges: CodeChange | null = null;

// GET /code - Return current code and screenshot
app.get('/code', async (req, res) => {
  const screenshot = await captureEditorScreenshot();
  res.json({
    filename: currentFile,
    code: currentCode,
    screenshot: screenshot, // base64 PNG
    line_count: currentCode.split('\n').length
  });
});

// POST /sketch - Receive sketch, interpret with AI, return suggested changes
app.post('/sketch', upload.single('sketch_image'), async (req, res) => {
  const sketchImage = req.file.buffer.toString('base64');
  const recognizedText = req.body.recognized_text || '';

  // Call Claude with vision to interpret the sketch
  const response = await anthropic.messages.create({
    model: 'claude-sonnet-4-20250514',
    max_tokens: 2048,
    messages: [{
      role: 'user',
      content: [
        {
          type: 'image',
          source: { type: 'base64', media_type: 'image/png', data: sketchImage }
        },
        {
          type: 'text',
          text: `You are a code editing assistant. This image shows code with hand-drawn annotations.

Recognized handwritten text: "${recognizedText}"

Current code:
\`\`\`python
${currentCode}
\`\`\`

Interpret the sketches and return JSON with:
- interpretation: what the user wants
- action: "insert" | "replace" | "delete"
- target_line: line number to modify
- new_code: the code to insert/replace`
        }
      ]
    }]
  });

  const result = parseAIResponse(response);
  pendingChanges = result;

  res.json({
    status: 'success',
    interpretation: result.interpretation,
    action: result.action,
    target_line: result.target_line,
    new_code: result.new_code,
    confidence: result.confidence
  });
});

// POST /decision - Apply or reject pending changes
app.post('/decision', express.json(), (req, res) => {
  if (req.body.action === 'accept' && pendingChanges) {
    currentCode = applyChanges(currentCode, pendingChanges);
    updateEditorContent(currentCode);
  }
  pendingChanges = null;

  res.json({
    status: req.body.action === 'accept' ? 'applied' : 'rejected',
    new_code_snapshot: await captureEditorScreenshot()
  });
});

app.listen(8080, '0.0.0.0');
```

### Android Main Screen (Kotlin + Compose)

```kotlin
@Composable
fun SketchCodeScreen(viewModel: SketchViewModel = viewModel()) {
    val codeSnapshot by viewModel.codeSnapshot.collectAsState()
    val interpretation by viewModel.interpretation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Code snapshot with sketch overlay
        Box(modifier = Modifier.weight(1f)) {
            // Background: Code screenshot from PC
            codeSnapshot?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Code",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Foreground: Sketch canvas
            SketchCanvas(
                modifier = Modifier.fillMaxSize(),
                onSketchUpdated = { /* local state only */ }
            )
        }

        // Interpretation result (shown after Send)
        interpretation?.let { result ->
            Card(modifier = Modifier.padding(8.dp)) {
                Text("AI Interpretation: ${result.interpretation}")
                Text("Action: ${result.action} at line ${result.targetLine}")
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Clear canvas
            OutlinedButton(onClick = { viewModel.clearCanvas() }) {
                Text("Clear")
            }

            // Send sketch to PC
            Button(
                onClick = { viewModel.sendSketch() },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp))
                else Text("Send")
            }

            // Accept/Reject (shown after interpretation)
            if (interpretation != null) {
                Button(
                    onClick = { viewModel.acceptChanges() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Accept ✓")
                }
                Button(
                    onClick = { viewModel.rejectChanges() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Reject ✗")
                }
            }
        }
    }
}
```

### ViewModel for API Calls (Kotlin)

```kotlin
class SketchViewModel : ViewModel() {
    private val api = RetrofitClient.sketchCodeApi

    private val _codeSnapshot = MutableStateFlow<Bitmap?>(null)
    val codeSnapshot: StateFlow<Bitmap?> = _codeSnapshot

    private val _interpretation = MutableStateFlow<InterpretationResult?>(null)
    val interpretation: StateFlow<InterpretationResult?> = _interpretation

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var currentSketchBitmap: Bitmap? = null

    init {
        fetchCodeSnapshot()
    }

    fun fetchCodeSnapshot() {
        viewModelScope.launch {
            val response = api.getCode()
            _codeSnapshot.value = response.screenshot.decodeBase64ToBitmap()
        }
    }

    fun sendSketch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val sketchImage = captureSketchOverlay()
                val response = api.postSketch(
                    sketchImage = sketchImage.toMultipartBody(),
                    recognizedText = runLocalHWR(sketchImage)
                )
                _interpretation.value = response
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptChanges() {
        viewModelScope.launch {
            api.postDecision(Decision("accept"))
            _interpretation.value = null
            fetchCodeSnapshot() // Refresh with new code
        }
    }

    fun rejectChanges() {
        viewModelScope.launch {
            api.postDecision(Decision("reject"))
            _interpretation.value = null
        }
    }
}
```

---

## Contact

**Team Name:** [Your Team Name]
**Members:** [Names and Emails]
**GitHub Repo:** [To be created]

---

*Generated for Snapdragon Multiverse Hackathon 2026*
