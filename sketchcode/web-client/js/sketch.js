/**
 * Sketch canvas engine for drawing annotations over code.
 * Supports touch, stylus (with pressure), and mouse input.
 */
const SketchCanvas = (() => {
  let canvas = null;
  let ctx = null;
  let isDrawing = false;
  let tool = 'pen'; // 'pen' or 'eraser'
  let penColor = '#ff3333';
  let penWidth = 3;
  let eraserWidth = 20;
  let lastPoint = null;
  let hasContent = false;

  function init(canvasEl) {
    canvas = canvasEl;
    ctx = canvas.getContext('2d');
    resizeCanvas();

    // Pointer events (unified touch/mouse/stylus)
    canvas.addEventListener('pointerdown', onPointerDown, { passive: false });
    canvas.addEventListener('pointermove', onPointerMove, { passive: false });
    canvas.addEventListener('pointerup', onPointerUp);
    canvas.addEventListener('pointerleave', onPointerUp);
    canvas.addEventListener('pointercancel', onPointerUp);

    // Prevent default touch behaviors
    canvas.addEventListener('touchstart', e => e.preventDefault(), { passive: false });
    canvas.addEventListener('touchmove', e => e.preventDefault(), { passive: false });

    // Resize on window resize
    window.addEventListener('resize', resizeCanvas);
  }

  function resizeCanvas() {
    if (!canvas) return;
    const container = canvas.parentElement;
    const rect = container.getBoundingClientRect();

    // Use scrollHeight for full code height, not just visible area
    const codeDisplay = document.getElementById('codeDisplay');
    const codeHeight = Math.max(codeDisplay.scrollHeight, rect.height);

    // Save existing content
    const imageData = hasContent ? ctx.getImageData(0, 0, canvas.width, canvas.height) : null;

    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = codeHeight * window.devicePixelRatio;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = codeHeight + 'px';

    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

    // Restore content
    if (imageData) {
      ctx.putImageData(imageData, 0, 0);
    }

    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
  }

  function onPointerDown(e) {
    e.preventDefault();
    isDrawing = true;
    const point = getPoint(e);
    lastPoint = point;

    ctx.beginPath();
    ctx.moveTo(point.x, point.y);

    if (tool === 'pen') {
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = penColor;
      ctx.lineWidth = getLineWidth(e);
    } else {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.lineWidth = eraserWidth;
    }
  }

  function onPointerMove(e) {
    if (!isDrawing) return;
    e.preventDefault();
    const point = getPoint(e);

    if (tool === 'pen') {
      ctx.lineWidth = getLineWidth(e);
      ctx.strokeStyle = penColor;
    }

    ctx.lineTo(point.x, point.y);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(point.x, point.y);

    lastPoint = point;
    hasContent = true;
  }

  function onPointerUp() {
    if (isDrawing) {
      isDrawing = false;
      ctx.beginPath();
      lastPoint = null;
    }
  }

  function getPoint(e) {
    const rect = canvas.getBoundingClientRect();
    // Account for scroll position of the container
    const container = canvas.parentElement;
    return {
      x: e.clientX - rect.left + container.scrollLeft,
      y: e.clientY - rect.top + container.scrollTop,
    };
  }

  function getLineWidth(e) {
    // Use pressure if available (stylus)
    const pressure = e.pressure || 0.5;
    return penWidth + pressure * 4;
  }

  function setTool(t) {
    tool = t;
  }

  function setColor(color) {
    penColor = color;
  }

  function clear() {
    if (!ctx) return;
    ctx.globalCompositeOperation = 'source-over';
    ctx.clearRect(0, 0, canvas.width / window.devicePixelRatio, canvas.height / window.devicePixelRatio);
    hasContent = false;
  }

  /**
   * Capture the code display + sketch overlay as a single PNG.
   * Uses html2canvas to render the code, then composites the sketch on top.
   */
  async function captureComposite() {
    const codeContainer = document.getElementById('codeContainer');

    // Temporarily hide the canvas for html2canvas
    canvas.style.visibility = 'hidden';

    // Capture the code display
    const codeCanvas = await html2canvas(codeContainer, {
      backgroundColor: '#1e1e1e',
      scale: 2,
      logging: false,
      useCORS: true,
      scrollX: 0,
      scrollY: 0,
      width: codeContainer.scrollWidth,
      height: codeContainer.scrollHeight,
    });

    canvas.style.visibility = 'visible';

    // Composite: code image + sketch overlay
    const compositeCanvas = document.createElement('canvas');
    compositeCanvas.width = codeCanvas.width;
    compositeCanvas.height = codeCanvas.height;
    const compCtx = compositeCanvas.getContext('2d');

    // Draw code background
    compCtx.drawImage(codeCanvas, 0, 0);

    // Draw sketch overlay
    compCtx.drawImage(canvas, 0, 0, compositeCanvas.width, compositeCanvas.height);

    // Convert to base64 PNG
    const dataUrl = compositeCanvas.toDataURL('image/png');
    return dataUrl.split(',')[1]; // Remove "data:image/png;base64," prefix
  }

  function hasDrawings() {
    return hasContent;
  }

  return { init, setTool, setColor, clear, captureComposite, hasDrawings, resizeCanvas };
})();
