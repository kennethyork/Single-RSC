package org.nemotech.rsc.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import org.nemotech.rsc.client.Shell;

/**
 * Android SurfaceView that hosts the RSC game rendering.
 * Handles touch input translation to the game's mouse/keyboard model.
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String VALID_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

    private Shell shell;
    private Bitmap frameBitmap;
    private final Paint paint = new Paint();
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();
    private boolean surfaceReady = false;

    // Touch state
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private boolean longPressActive = false;
    private boolean pinchActive = false;
    private float twoFingerStartX;
    private float twoFingerLastX;
    private boolean twoFingerDragging = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Virtual keyboard state
    private boolean keyboardVisible = false;



    // Actual rendered frame dimensions (may differ from panelWidth/panelHeight)
    private volatile int frameWidth;
    private volatile int frameHeight;

    // Keyboard height in screen pixels (0 when hidden)
    private volatile int keyboardHeight = 0;
    private final Rect visibleFrame = new Rect();

    public GameSurfaceView(Context context) {
        super(context);
        init();
    }

    public GameSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);

        // Detect soft keyboard height by comparing root view to visible display frame
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                View rootView = getRootView();
                rootView.getWindowVisibleDisplayFrame(visibleFrame);
                int screenHeight = rootView.getHeight();
                int visibleHeight = visibleFrame.height();
                int kbHeight = screenHeight - visibleHeight;
                // Only treat as keyboard if >15% of screen height
                keyboardHeight = (kbHeight > screenHeight * 0.15) ? kbHeight : 0;
            }
        });

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (shell == null) return false;
                int[] gameCoords = screenToGame(e.getX(), e.getY());
                if (gameCoords == null) return false;

                // Single tap = left click
                synchronized (shell) {
                    shell.mouseX = gameCoords[0];
                    shell.mouseY = gameCoords[1];
                    shell.mouseButtonDown = 1;
                    shell.lastMouseButtonDown = 1;
                }
                // Release after a short delay
                handler.postDelayed(() -> {
                    synchronized (shell) {
                        shell.mouseButtonDown = 0;
                    }
                }, 50);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (shell == null) return;
                int[] gameCoords = screenToGame(e.getX(), e.getY());
                if (gameCoords == null) return;
                // Long press = right click
                longPressActive = true;
                synchronized (shell) {
                    shell.mouseX = gameCoords[0];
                    shell.mouseY = gameCoords[1];
                    shell.mouseButtonDown = 2;
                    shell.lastMouseButtonDown = 2;
                }
                handler.postDelayed(() -> {
                    synchronized (shell) {
                        shell.mouseButtonDown = 0;
                    }
                    longPressActive = false;
                }, 100);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Double-tap toggles virtual keyboard
                toggleKeyboard();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (shell == null) return false;
                // Scroll = mouse wheel (for scrolling lists)
                int rotation = dy > 0 ? 1 : -1;
                shell.handleMouseScroll(rotation);
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(true);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private int zoomAtStart;

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (shell == null) return false;
                pinchActive = true;
                zoomAtStart = shell.cameraZoomOffset;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (shell == null) return false;
                // scaleFactor > 1 = pinch out (zoom in = lower zoom value = closer camera)
                // scaleFactor < 1 = pinch in (zoom out = higher zoom value = farther camera)
                float totalScale = detector.getScaleFactor();
                // Invert: pinch out should decrease zoom (move camera closer)
                int delta = (int) ((1.0f - totalScale) * 300);
                shell.cameraZoomOffset = Math.max(-250, Math.min(350, zoomAtStart + delta));
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                pinchActive = false;
            }
        });
    }

    public void setShell(Shell shell) {
        this.shell = shell;
    }

    /**
     * Called by the game thread to render a frame.
     * Copies the pixel buffer to a Bitmap and draws it to the SurfaceView.
     */
    public void renderFrame(int[] pixels, int width, int height) {
        if (!surfaceReady || width <= 0 || height <= 0) return;

        if (frameBitmap == null || frameBitmap.getWidth() != width || frameBitmap.getHeight() != height) {
            if (frameBitmap != null) frameBitmap.recycle();
            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        frameWidth = width;
        frameHeight = height;

        // Copy pixel data to bitmap (game uses 0xRRGGBB, Android expects 0xAARRGGBB)
        for (int i = 0; i < pixels.length && i < width * height; i++) {
            pixels[i] = pixels[i] | 0xFF000000; // Set alpha to opaque
        }
        frameBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        SurfaceHolder holder = getHolder();
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.BLACK);
                srcRect.set(0, 0, width, height);

                // Reduce available height by keyboard
                int availW = canvas.getWidth();
                int availH = canvas.getHeight() - keyboardHeight;
                if (availH < 100) availH = canvas.getHeight(); // fallback

                // Scale to fill available area, maintaining aspect ratio
                float scaleX = (float) availW / width;
                float scaleY = (float) availH / height;
                float scale = Math.min(scaleX, scaleY);
                int dw = (int) (width * scale);
                int dh = (int) (height * scale);
                int dx = (availW - dw) / 2;
                int dy = (availH - dh) / 2;
                dstRect.set(dx, dy, dx + dw, dy + dh);

                canvas.drawBitmap(frameBitmap, srcRect, dstRect, paint);
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    /** Convert screen coordinates to game coordinates. */
    private int[] screenToGame(float screenX, float screenY) {
        if (shell == null) return null;
        SurfaceHolder holder = getHolder();
        if (holder.getSurface() == null) return null;

        int canvasW = getWidth();
        int canvasH = getHeight() - keyboardHeight;
        if (canvasH < 100) canvasH = getHeight();
        // Use actual rendered frame size to match renderFrame() scaling
        int gameW = frameWidth > 0 ? frameWidth : shell.panelWidth;
        int gameH = frameHeight > 0 ? frameHeight : shell.panelHeight;

        float scaleX = (float) canvasW / gameW;
        float scaleY = (float) canvasH / gameH;
        float scale = Math.min(scaleX, scaleY);
        int dw = (int) (gameW * scale);
        int dh = (int) (gameH * scale);
        int dx = (canvasW - dw) / 2;
        int dy = (canvasH - dh) / 2;

        int gx = (int) ((screenX - dx) / scale);
        int gy = (int) ((screenY - dy) / scale);

        if (gx < 0 || gx >= gameW || gy < 0 || gy >= gameH) return null;
        return new int[]{gx, gy};
    }



    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_DONE;
        return new GameInputConnection(this);
    }

    /**
     * Custom InputConnection that intercepts text committed by the soft keyboard
     * and routes it to the game's input system. Modern Android keyboards use
     * commitText/deleteSurroundingText instead of generating KeyEvents.
     */
    private class GameInputConnection extends BaseInputConnection {
        GameInputConnection(View targetView) {
            super(targetView, false);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    processCharInput(text.charAt(i));
                }
            }
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            // Treat composing text as committed text
            return commitText(text, newCursorPosition);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            for (int i = 0; i < beforeLength; i++) {
                processBackspace();
            }
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            // Some keyboards still send key events — forward them
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return onKeyDown(event.getKeyCode(), event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                return onKeyUp(event.getKeyCode(), event);
            }
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean performEditorAction(int actionCode) {
            // IME "Done" button acts as Enter
            processEnter();
            return true;
        }
    }

    /** Process a single character from keyboard (soft or hardware). */
    private void processCharInput(char c) {
        if (shell == null || c == 0) return;
        synchronized (shell) {
            shell.handleKeyPress(c);
            if (VALID_CHARS.indexOf(c) >= 0 && shell.inputTextCurrent.length() < 20) {
                shell.inputTextCurrent += c;
            }
        }
    }

    /** Process backspace from keyboard. */
    private void processBackspace() {
        if (shell == null) return;
        synchronized (shell) {
            shell.handleKeyPress(8);
            if (shell.inputTextCurrent.length() > 0) {
                shell.inputTextCurrent = shell.inputTextCurrent.substring(0,
                        shell.inputTextCurrent.length() - 1);
            }
        }
    }

    /** Process enter/return from keyboard. */
    private void processEnter() {
        if (shell == null) return;
        synchronized (shell) {
            shell.handleKeyPress(10);
            shell.inputTextFinal = shell.inputTextCurrent;
        }
    }

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboardVisible) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        } else {
            requestFocus();
            imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
        }
        keyboardVisible = !keyboardVisible;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        // Track two-finger horizontal drag for camera rotation
        if (pointerCount == 2 && action == MotionEvent.ACTION_MOVE) {
            float midX = (event.getX(0) + event.getX(1)) / 2f;
            if (!twoFingerDragging) {
                twoFingerDragging = true;
                twoFingerStartX = midX;
                twoFingerLastX = midX;
            } else if (shell != null) {
                float dx = midX - twoFingerLastX;
                // Convert screen pixels to rotation units (~4 pixels per rotation step)
                int rotSteps = (int) (dx / 4f);
                if (rotSteps != 0) {
                    shell.cameraRotationDelta += rotSteps;
                    twoFingerLastX = midX;
                }
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            twoFingerDragging = false;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && pointerCount <= 2) {
            twoFingerDragging = false;
        }

        if (!pinchActive && !twoFingerDragging) {
            gestureDetector.onTouchEvent(event);
        }

        if (shell == null) return true;

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (!longPressActive && !pinchActive && pointerCount == 1) {
                    int[] coords = screenToGame(event.getX(), event.getY());
                    if (coords != null) {
                        synchronized (shell) {
                            shell.mouseX = coords[0];
                            shell.mouseY = coords[1];
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                synchronized (shell) {
                    shell.mouseButtonDown = 0;
                }
                longPressActive = false;
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (shell == null) return super.onKeyDown(keyCode, event);

        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                processBackspace();
                return true;
            case KeyEvent.KEYCODE_ENTER:
                processEnter();
                return true;
            case KeyEvent.KEYCODE_TAB:
                synchronized (shell) {
                    shell.handleKeyPress(9);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                shell.keyLeft = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                shell.keyRight = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                shell.keyUp = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                shell.keyDown = true;
                return true;
            case KeyEvent.KEYCODE_SPACE:
                shell.keySpace = true;
                processCharInput(' ');
                return true;
            default:
                char c = (char) event.getUnicodeChar();
                if (c != 0) {
                    processCharInput(c);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (shell == null) return super.onKeyUp(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                shell.keyLeft = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                shell.keyRight = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                shell.keyUp = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                shell.keyDown = false;
                return true;
            case KeyEvent.KEYCODE_SPACE:
                shell.keySpace = false;
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceReady = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }
}
