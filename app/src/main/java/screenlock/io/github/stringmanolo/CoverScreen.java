package io.github.stringmanolo.screenlock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;

import java.util.ArrayList;
import java.util.Collections;

import android.view.accessibility.AccessibilityEvent;

public class CoverScreen extends AccessibilityService {

    private WindowManager windowManager;
    private FrameLayout overlay;
    private WindowManager.LayoutParams overlayParams;
    private Handler mainHandler;

    private Button unlockButton;
    private Button crashButton;
    private Button changePinButton;
    private EditText pinField;
    private GridLayout keyboardGrid;
    private EditText feedbackText;

    private int originalHeight;

    private BroadcastReceiver screenReceiver;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "CoverScreenPrefs";
    private static final String KEY_PIN = "pin";

    private String tempNewPin = "";

    private enum PinState { NORMAL, ENTER_OLD, ENTER_NEW, REPEAT_NEW }
    private PinState pinState = PinState.NORMAL;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!prefs.contains(KEY_PIN)) {
            prefs.edit().putString(KEY_PIN, "1234").apply();
        }

        setupOverlay();
        registerScreenReceiver();
    }

    private void setupOverlay() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

                overlay = new FrameLayout(CoverScreen.this);
                overlay.setBackgroundColor(Color.parseColor("#ff000000"));

                feedbackText = new EditText(CoverScreen.this);
                feedbackText.setTextColor(Color.YELLOW);
                feedbackText.setTextSize(18);
                feedbackText.setFocusable(false);
                feedbackText.setBackgroundColor(Color.TRANSPARENT);
                feedbackText.setGravity(Gravity.CENTER);
                feedbackText.setText("Introduce el PIN");

                FrameLayout.LayoutParams feedbackParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                feedbackParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                feedbackParams.topMargin = 20;
                overlay.addView(feedbackText, feedbackParams);

                pinField = new EditText(CoverScreen.this);
                pinField.setTextColor(Color.WHITE);
                pinField.setTextSize(24);
                pinField.setFocusable(false);
                pinField.setGravity(Gravity.CENTER);
                pinField.setBackgroundColor(Color.TRANSPARENT);

                FrameLayout.LayoutParams pinParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                pinParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                pinParams.topMargin = 50;
                overlay.addView(pinField, pinParams);

                keyboardGrid = new GridLayout(CoverScreen.this);
                keyboardGrid.setColumnCount(3);
                FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                gridParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                gridParams.topMargin = 150;
                overlay.addView(keyboardGrid, gridParams);

                shuffleKeyboard();

                crashButton = new Button(CoverScreen.this);
                crashButton.setText("Crash");
                crashButton.setTextColor(Color.WHITE);
                crashButton.setBackgroundColor(Color.RED);
                crashButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (overlay != null && windowManager != null) {
                            windowManager.removeView(overlay);
                            overlay = null;
                        }
                        stopSelf();
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    }
                });
                FrameLayout.LayoutParams paramsCrash = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                paramsCrash.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                paramsCrash.topMargin = 110;
                overlay.addView(crashButton, paramsCrash);
                crashButton.bringToFront();

                setupUnlockButton();
                setupChangePinButton();

                overlayParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        android.graphics.PixelFormat.TRANSLUCENT
                );

                originalHeight = overlayParams.height;
                windowManager.addView(overlay, overlayParams);
            }
        });
    }

    private void setupUnlockButton() {
        unlockButton = new Button(CoverScreen.this);
        unlockButton.setText("Desbloquear");
        unlockButton.setTextColor(Color.WHITE);
        unlockButton.setBackgroundColor(Color.TRANSPARENT);
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pinState != PinState.NORMAL) {
                    feedbackText.setText("Completa el flujo de cambio de PIN primero");
                    return;
                }
                String enteredPin = pinField.getText().toString();
                String savedPin = prefs.getString(KEY_PIN, "1234");
                if (enteredPin.equals(savedPin)) {
                    pinField.setText("");
                    feedbackText.setText("");
                    reduceOverlayHeight();
                } else {
                    pinField.setText("");
                    feedbackText.setText("PIN incorrecto");
                }
            }
        });

        FrameLayout.LayoutParams paramsUnlock = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        paramsUnlock.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        paramsUnlock.bottomMargin = 50;
        overlay.addView(unlockButton, paramsUnlock);
    }

    private void setupChangePinButton() {
        changePinButton = new Button(CoverScreen.this);
        changePinButton.setText("Cambiar PIN");
        changePinButton.setTextColor(Color.WHITE);
        changePinButton.setBackgroundColor(Color.TRANSPARENT);
        changePinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String enteredPin = pinField.getText().toString();
                String savedPin = prefs.getString(KEY_PIN, "1234");

                switch (pinState) {
                    case NORMAL:
                        pinState = PinState.ENTER_OLD;
                        pinField.setText("");
                        feedbackText.setText("Introduce PIN actual y pulsa Cambiar PIN");
                        break;
                    case ENTER_OLD:
                        if (enteredPin.equals(savedPin)) {
                            pinState = PinState.ENTER_NEW;
                            pinField.setText("");
                            feedbackText.setText("PIN correcto, introduce nuevo PIN y pulsa Cambiar PIN");
                        } else {
                            pinField.setText("");
                            feedbackText.setText("PIN incorrecto");
                        }
                        break;
                    case ENTER_NEW:
                        if (!enteredPin.isEmpty()) {
                            tempNewPin = enteredPin;
                            pinState = PinState.REPEAT_NEW;
                            pinField.setText("");
                            feedbackText.setText("Repite el nuevo PIN y pulsa Cambiar PIN");
                        } else {
                            feedbackText.setText("El PIN no puede estar vacío");
                        }
                        break;
                    case REPEAT_NEW:
                        if (enteredPin.equals(tempNewPin)) {
                            prefs.edit().putString(KEY_PIN, tempNewPin).apply();
                            pinState = PinState.NORMAL;
                            pinField.setText("");
                            feedbackText.setText("PIN cambiado");
                        } else {
                            pinState = PinState.ENTER_OLD;
                            pinField.setText("");
                            feedbackText.setText("El nuevo PIN no coincide. Introduce PIN actual y pulsa Cambiar PIN");
                        }
                        break;
                }
            }
        });

        FrameLayout.LayoutParams paramsChangePin = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        paramsChangePin.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        paramsChangePin.bottomMargin = 150;
        overlay.addView(changePinButton, paramsChangePin);
    }

    private void reduceOverlayHeight() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (overlayParams != null && overlay != null && windowManager != null) {
                    overlayParams.height = 0;
                    overlayParams.gravity = Gravity.TOP;
                    windowManager.updateViewLayout(overlay, overlayParams);

                    if (crashButton != null) crashButton.bringToFront();

                    if (unlockButton != null) overlay.removeView(unlockButton);
                    if (changePinButton != null) overlay.removeView(changePinButton);
                    if (keyboardGrid != null) keyboardGrid.setVisibility(View.GONE);
                    if (pinField != null) pinField.setVisibility(View.GONE);
                }
            }
        });
    }

    private void shuffleKeyboard() {
        keyboardGrid.removeAllViews();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int buttonSize = screenWidth / 4;

        ArrayList<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 9; i++) numbers.add(i);
        Collections.shuffle(numbers);

        for (final int num : numbers) {
            Button btn = new Button(CoverScreen.this);
            btn.setText(String.valueOf(num));
            btn.setTextSize(18);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pinField != null) pinField.append(String.valueOf(num));
                }
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = buttonSize;
            params.height = buttonSize;
            params.setMargins(10, 10, 10, 10);
            keyboardGrid.addView(btn, params);
        }
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (overlayParams != null && overlay != null && windowManager != null) {
                                overlayParams.height = originalHeight;
                                overlayParams.gravity = Gravity.TOP;
                                windowManager.updateViewLayout(overlay, overlayParams);

                                if (keyboardGrid != null) keyboardGrid.setVisibility(View.VISIBLE);
                                if (pinField != null) {
                                    pinField.setVisibility(View.VISIBLE);
                                    pinField.setText("");
                                }

                                // Restaurar botones siempre
                                if (unlockButton != null) {
                                    overlay.removeView(unlockButton);
                                    overlay.addView(unlockButton);
                                }
                                if (changePinButton != null) {
                                    overlay.removeView(changePinButton);
                                    overlay.addView(changePinButton);
                                }

                                if (crashButton != null) crashButton.bringToFront();

                                shuffleKeyboard();
                                pinState = PinState.NORMAL;
                                feedbackText.setText("Introduce el PIN");
                            }
                        }
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlay != null && windowManager != null) {
            windowManager.removeView(overlay);
        }
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
            screenReceiver = null;
        }
    }
}
