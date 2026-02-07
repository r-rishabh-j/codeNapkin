/**
 * Main application: wires together WebSocket, sketch canvas, and voice.
 */
(function () {
  // DOM elements
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const fileInfo = document.getElementById('fileInfo');
  const codeBlock = document.getElementById('codeBlock');
  const codeContainer = document.getElementById('codeContainer');
  const sketchCanvas = document.getElementById('sketchCanvas');
  const voiceDisplay = document.getElementById('voiceDisplay');
  const voiceText = document.getElementById('voiceText');
  const penBtn = document.getElementById('penBtn');
  const eraserBtn = document.getElementById('eraserBtn');
  const colorPicker = document.getElementById('colorPicker');
  const clearBtn = document.getElementById('clearBtn');
  const voiceBtn = document.getElementById('voiceBtn');
  const sendBtn = document.getElementById('sendBtn');

  let lastCodeTimestamp = 0;

  // --- Initialize modules ---
  SketchCanvas.init(sketchCanvas);
  VoiceRecorder.init();

  // --- WebSocket events ---
  SketchCodeWS.on('connected', () => {
    statusDot.classList.add('connected');
    statusDot.classList.remove('error');
    statusText.textContent = 'Connected';
  });

  SketchCodeWS.on('disconnected', () => {
    statusDot.classList.remove('connected');
    statusText.textContent = 'Disconnected';
  });

  SketchCodeWS.on('reconnecting', ({ attempt, delay }) => {
    statusText.textContent = `Reconnecting (${attempt})...`;
  });

  SketchCodeWS.on('error', (msg) => {
    statusDot.classList.add('error');
    statusText.textContent = 'Error: ' + msg;
  });

  SketchCodeWS.on('code_update', (payload) => {
    lastCodeTimestamp = payload.timestamp;
    updateCodeDisplay(payload);
  });

  // --- Code display ---
  function updateCodeDisplay(payload) {
    fileInfo.textContent = `${payload.filename} (${payload.language})`;

    // Add line numbers
    const lines = payload.code.split('\n');
    const numberedCode = lines
      .map((line, i) => {
        const num = String(i + 1).padStart(4, ' ');
        return `<span class="line-num">${num}</span>  ${escapeHtml(line)}`;
      })
      .join('\n');

    codeBlock.innerHTML = numberedCode;

    // Apply syntax highlighting to the code (just the text, not line numbers)
    // Re-highlight by setting textContent and calling hljs
    const tempEl = document.createElement('code');
    tempEl.className = payload.language ? `language-${payload.language}` : '';
    tempEl.textContent = payload.code;
    hljs.highlightElement(tempEl);

    // Build line-numbered output with highlighted code
    const highlightedLines = tempEl.innerHTML.split('\n');
    const finalHtml = highlightedLines
      .map((line, i) => {
        const num = String(i + 1).padStart(4, ' ');
        return `<span style="color:#858585;user-select:none">${num}  </span>${line}`;
      })
      .join('\n');

    codeBlock.innerHTML = finalHtml;

    // Resize canvas to match new code height
    requestAnimationFrame(() => SketchCanvas.resizeCanvas());
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // --- Tool buttons ---
  penBtn.addEventListener('click', () => {
    SketchCanvas.setTool('pen');
    penBtn.classList.add('active');
    eraserBtn.classList.remove('active');
  });

  eraserBtn.addEventListener('click', () => {
    SketchCanvas.setTool('eraser');
    eraserBtn.classList.add('active');
    penBtn.classList.remove('active');
  });

  colorPicker.addEventListener('input', (e) => {
    SketchCanvas.setColor(e.target.value);
  });

  clearBtn.addEventListener('click', () => {
    SketchCanvas.clear();
  });

  // --- Voice ---
  voiceBtn.addEventListener('click', () => {
    VoiceRecorder.toggle();
  });

  VoiceRecorder.on('started', () => {
    voiceBtn.classList.add('recording');
    voiceDisplay.classList.remove('hidden');
    voiceDisplay.classList.add('recording');
    voiceText.textContent = 'Listening...';
  });

  VoiceRecorder.on('stopped', () => {
    voiceBtn.classList.remove('recording');
    voiceDisplay.classList.remove('recording');
    if (!VoiceRecorder.getTranscription()) {
      voiceDisplay.classList.add('hidden');
    }
  });

  VoiceRecorder.on('result', ({ text, isFinal }) => {
    voiceText.textContent = text;
    if (!isFinal) {
      voiceText.style.opacity = '0.7';
    } else {
      voiceText.style.opacity = '1';
    }
  });

  VoiceRecorder.on('unsupported', () => {
    voiceBtn.style.opacity = '0.3';
    voiceBtn.title = 'Voice not supported in this browser';
    voiceBtn.disabled = true;
  });

  VoiceRecorder.on('error', (err) => {
    voiceText.textContent = 'Error: ' + err;
  });

  // --- Send ---
  sendBtn.addEventListener('click', async () => {
    if (!SketchCodeWS.isConnected()) {
      alert('Not connected to VSCode');
      return;
    }

    if (!SketchCanvas.hasDrawings() && !VoiceRecorder.getTranscription()) {
      alert('Draw an annotation or record a voice command first');
      return;
    }

    sendBtn.disabled = true;
    sendBtn.textContent = 'Capturing...';

    try {
      // Capture composite image
      const sketchImageBase64 = await SketchCanvas.captureComposite();
      const voiceTranscription = VoiceRecorder.getTranscription();

      // Send via WebSocket
      const sent = SketchCodeWS.sendAnnotation(
        sketchImageBase64,
        voiceTranscription,
        lastCodeTimestamp
      );

      if (sent) {
        sendBtn.textContent = 'Sent!';
        sendBtn.classList.add('sent');

        // Clear canvas and voice
        SketchCanvas.clear();
        VoiceRecorder.clearTranscription();
        voiceDisplay.classList.add('hidden');

        setTimeout(() => {
          sendBtn.textContent = 'Send';
          sendBtn.classList.remove('sent');
          sendBtn.disabled = false;
        }, 2000);
      } else {
        throw new Error('Failed to send');
      }
    } catch (err) {
      sendBtn.textContent = 'Error!';
      console.error('Send failed:', err);
      setTimeout(() => {
        sendBtn.textContent = 'Send';
        sendBtn.disabled = false;
      }, 2000);
    }
  });

  // --- Start connection ---
  SketchCodeWS.connect();
})();
