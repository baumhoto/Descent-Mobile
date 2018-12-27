package tuchsen.descent;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class DescentView extends SurfaceView implements KeyEvent.Callback,  SurfaceHolder.Callback {
	private boolean descentRunning, paused, surfaceWasDestroyed, textActive;
	private Context context;
	private DescentView thiz;
	private Handler mainHandler;
	private InputMethodManager imm;
	private final Object renderThreadObj = new Object();
	private Point size;
	private SurfaceHolder holder;

	public DescentView(Context context) {
		super(context);
		this.context = context;
		this.descentRunning = false;
		this.holder = getHolder();
		this.thiz = this;
		this.textActive = false;
		this.imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		this.mainHandler = new Handler(context.getMainLooper());
		this.setFocusableInTouchMode(true);
		holder.addCallback(this);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		int i, historySize, firstPointerIndex, numPointers;
		int action = event.getActionMasked();
		float prevX, prevY;
		boolean touchHandled = false;

		historySize = event.getHistorySize();
		if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
			firstPointerIndex = event.getActionIndex();
		} else {
			firstPointerIndex = 0;
		}
		if (action == MotionEvent.ACTION_MOVE) {
			numPointers = event.getPointerCount();
		} else {
			numPointers = 1;
		}
		for (i = firstPointerIndex; i < numPointers + firstPointerIndex; ++i) {
			if (historySize > 0) {
				prevX = event.getHistoricalX(i, 0);
				prevY = event.getHistoricalY(i, 0);
			} else {
				prevX = event.getX(i);
				prevY = event.getY(i);
			}
			touchHandled |= touchHandler(event.getActionMasked(), event.getPointerId(i),
					event.getX(i), event.getY(i), prevX, prevY);
		}
		if (!touchHandled && (action == MotionEvent.ACTION_DOWN ||
				action == MotionEvent.ACTION_UP)) {
			mouseHandler((short) event.getX(), (short) event.getY(),
					action == MotionEvent.ACTION_DOWN);
			return true;
		} else {
			mouseSetPos((short) event.getX(), (short) event.getY());
		}
		return touchHandled;
	}

	@SuppressWarnings("unused")
	private boolean textIsActive() {
		return textActive;
	}

	@SuppressWarnings("unused")
	private void activateText() {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				requestFocus();
				imm.showSoftInput(thiz, InputMethodManager.SHOW_FORCED);
				textActive = true;
			}
		});
	}

	@SuppressWarnings("unused")
	private void deactivateText() {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				imm.hideSoftInputFromWindow(getWindowToken(), 0);
				clearFocus();
				textActive = false;
			}
		});
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		char result = getCharForKeyCode(keyCode, event);

		keyHandler(result, false);

		return event.getUnicodeChar() != 0;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		char result = getCharForKeyCode(keyCode, event);

		keyHandler(result, true);

		return event.getUnicodeChar() != 0;
	}

	private static boolean isFireKey(int keyCode) {
		// Here we treat Button_A and DPAD_CENTER as the primary action
		// keys for the game.
		return keyCode == KeyEvent.KEYCODE_BUTTON_A;
	}

	private char getCharForKeyCode(int keyCode, KeyEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_GAMEPAD)
				== InputDevice.SOURCE_GAMEPAD) {
			if (event.getRepeatCount() == 0) {
				switch (keyCode) {
					default:
						if (isFireKey(keyCode)) {
							return (char) 0x1d;
							// Update the ship object to fire lasers
						}
						break;
				}
			}
		}
			else
			{
				if (keyCode == KeyEvent.KEYCODE_DEL) {
					return (char) 0x0E;
				} else if (keyCode == KeyEvent.KEYCODE_ENTER) {
					return (char) 0x1C;
				} else {
					return (char) event.getUnicodeChar();
				}
			}

		return (char) event.getUnicodeChar();
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {

		// Check that the event came from a game controller
		if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
				InputDevice.SOURCE_JOYSTICK &&
				event.getAction() == MotionEvent.ACTION_MOVE) {

			// Process all historical movement samples in the batch
			final int historySize = event.getHistorySize();

			// Process the movements starting from the
			// earliest historical position in the batch
			for (int i = 0; i < historySize; i++) {
				// Process the event at historical position i
				processJoystickInput(event, i);
			}

			// Process the current movement sample in the batch (position -1)
			processJoystickInput(event, -1);
			return true;
		}
		return super.onGenericMotionEvent(event);
	}


	private static float getCenteredAxis(MotionEvent event,
										 InputDevice device, int axis, int historyPos) {
		final InputDevice.MotionRange range =
				device.getMotionRange(axis, event.getSource());

		// A joystick at rest does not always report an absolute position of
		// (0,0). Use the getFlat() method to determine the range of values
		// bounding the joystick axis center.
		if (range != null) {
			final float flat = range.getFlat();
			final float value =
					historyPos < 0 ? event.getAxisValue(axis):
							event.getHistoricalAxisValue(axis, historyPos);

			// Ignore axis values that are within the 'flat' region of the
			// joystick axis center.
			if (Math.abs(value) > flat) {
				return value;
			}
		}
		return 0;
	}

    private void processJoystickInput(MotionEvent event, int historyPos) {

        InputDevice mInputDevice = event.getDevice();

        // Calculate the horizontal distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat axis, or the right control stick.
        float x = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_X, historyPos);
        }
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_Z, historyPos);
        }

        // Calculate the vertical distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat switch, or the right control stick.
        float y = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_Y, historyPos);
        }
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_RZ, historyPos);
        }

        // Update the ship object based on the new x and y values
    }


	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && textActive) {
			keyHandler((char) 0x01, true);
			keyHandler((char) 0x01, false);
			return true;
		}
		return false;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void surfaceCreated(SurfaceHolder holder) {
		if (!descentRunning) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
					Display display = wm.getDefaultDisplay();
					size = new Point();
					if (Build.VERSION.SDK_INT >= 19) {
						display.getRealSize(size);
					} else if (Build.VERSION.SDK_INT >= 13) {
						display.getSize(size);
					} else {
						size.set(display.getWidth(), display.getHeight());
					}
					initEgl();

					// Start Descent!
					descentMain(size.x, size.y, context, thiz, context.getAssets(),
							context.getFilesDir().getAbsolutePath(),
							context.getCacheDir().getAbsolutePath());
				}
			}).start();
			descentRunning = true;
		} else {
			this.holder = holder;
			resumeRenderThread();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		surfaceDestroyed(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceWasDestroyed = true;
		surfaceWasDestroyed();
	}

	public boolean getSurfaceWasDestroyed() {
		return surfaceWasDestroyed;
	}

	public void resumeRenderThread() {
		synchronized (renderThreadObj) {
			paused = false;
			surfaceWasDestroyed = false;
			renderThreadObj.notifyAll();
		}
	}

	@SuppressWarnings("unused")
	private void pauseRenderThread() {
		synchronized (renderThreadObj) {
			paused = true;
			while (paused) {
				try {
					renderThreadObj.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Initializes EGL for the current thread
	 */
	private void initEgl() {
		int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
		int[] num_config = new int[1];
		final EGLConfig configs[] = new EGLConfig[1];
		int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 1, EGL10.EGL_NONE};
		EGL10 egl;
		EGLConfig eglConfig;
		EGLContext eglContext;
		EGLDisplay eglDisplay;
		EGLSurface eglSurface;
		GL10 gl;

		egl = (EGL10) EGLContext.getEGL();
		eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
		egl.eglInitialize(eglDisplay, new int[]{1, 0});
		egl.eglChooseConfig(eglDisplay, new int[]{
				EGL10.EGL_RED_SIZE, 8,
				EGL10.EGL_GREEN_SIZE, 8,
				EGL10.EGL_BLUE_SIZE, 8,
				EGL10.EGL_ALPHA_SIZE, 8,
				EGL10.EGL_DEPTH_SIZE, 16,
				EGL10.EGL_STENCIL_SIZE, 0,
				EGL10.EGL_NONE}, configs, 1, num_config);
		eglConfig = configs[0];
		eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
		eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, holder, null);
		egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
		gl = (GL10) eglContext.getGL();

		// Clear to back
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		// Enable culling
		gl.glEnable(GL10.GL_CULL_FACE);
		gl.glCullFace(GL10.GL_BACK);

		// Viewport is screen bounds
		gl.glViewport(0, 0, size.x, size.y);

		// Enable blending for alphas
		gl.glEnable(GL10.GL_BLEND);
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static native void keyHandler(char key, boolean down);

	private static native void mouseHandler(short x, short y, boolean down);

	private static native void mouseSetPos(short x, short y);

	private static native boolean touchHandler(int action, int pointerId, float x, float y,
											   float prevX, float prevY);

	private static native void surfaceWasDestroyed();

	private static native void descentMain(int w, int h, Context activity,
										   DescentView descentView,
										   AssetManager assetManager, String documentPath,
										   String cachePath);
}
