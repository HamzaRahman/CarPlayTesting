package com.debug.cansourcetester;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Debug tool for probing the head unit's shared "SourceInfo" / "McuManager"
 * system services (as reverse-engineered from the OEM Radio app).
 *
 * IMPORTANT: The class names below (android.sourceservice.SourceInfo,
 * android.carsource.McuManager) are NOT part of the public Android SDK.
 * They only exist on this specific head unit's firmware. We call them
 * entirely via reflection so this project compiles normally against the
 * public SDK, and the calls simply fail (and get logged) on any device
 * that doesn't have these classes.
 */
public class MainActivity extends Activity {

    private static final String TAG = "CanSourceTester";

    private TextView logView;
    private EditText editTitle;

    private MediaSessionCompat mediaSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int fakePositionMs = 0;
    private int fakeDurationMs = 3 * 60 * 1000; // 3:00 fake duration
    private boolean sessionRunning = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sessionRunning) return;
            fakePositionMs += 1000;
            if (fakePositionMs > fakeDurationMs) fakePositionMs = 0;
            publishPlaybackState();
            log("tick: position=" + formatMs(fakePositionMs) + " / " + formatMs(fakeDurationMs));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = findViewById(R.id.logView);
        logView.setMovementMethod(new ScrollingMovementMethod());
        editTitle = findViewById(R.id.editTitle);

        Button btnRadio = findViewById(R.id.btnRadio);
        Button btnUsb = findViewById(R.id.btnUsb);
        Button btnBt = findViewById(R.id.btnBt);
        Button btnAux = findViewById(R.id.btnAux);
        Button btnStart = findViewById(R.id.btnStartSession);
        Button btnStop = findViewById(R.id.btnStopSession);
        Button btnKeyBt = findViewById(R.id.btnKeyBt);
        Button btnKeyBtMusic = findViewById(R.id.btnKeyBtMusic);
        Button btnKeyMusic = findViewById(R.id.btnKeyMusic);
        Button btnFuelInfo = findViewById(R.id.btnFuelInfo);

        // Step 1: claim source. Confirmed by decompiling the real BluetoothService.apk
        // (android.sourceservice.SourceInfo, android.sourceservice.SourceService):
        // onRequestPlayAudio(String) appends "/background" to whatever we pass, and the
        // service matches the result against fixed ACTIVE_* package constants by prefix
        // (e.g. ACTIVE_RADIO="com.hcn.autoradio", ACTIVE_BT="com.autochips.bluetooth").
        // The real Bluetooth app literally calls:
        //   mSourceInfo.onRequestPlayAudio("com.autochips.bluetooth/.BtMusicActivity")
        // Our earlier guess "com.android.bluetooth" never matched ACTIVE_BT, hence no-op.
        // USB isn't claimed via SourceInfo at all (Music.apk never references it) — it's
        // handled at a lower, automatic level, matching that USB already worked on iMID.
        // AUX has no dedicated audio-in constant; ACTIVE_AUX="com.auto.hcamera" is the
        // backup-camera package, so "com.hcn.audioinputsource" (referenced elsewhere in
        // SourceService as a distinct, specially-handled package) is the best candidate
        // for a physical AUX-in source — untested, flag it clearly in the log.
        btnRadio.setOnClickListener(v -> claimSource("com.hcn.autoradio"));
        btnUsb.setOnClickListener(v -> claimSource("com.android.usb"));
        btnBt.setOnClickListener(v -> claimSource("com.autochips.bluetooth/.BtMusicActivity"));
        btnAux.setOnClickListener(v -> claimSource("com.hcn.audioinputsource"));

        btnStart.setOnClickListener(v -> startFakeSession());
        btnStop.setOnClickListener(v -> stopFakeSession());

        btnKeyBt.setOnClickListener(v -> injectMcuKey(302, 0));       // K_BT
        btnKeyBtMusic.setOnClickListener(v -> injectMcuKey(331, 0));  // K_BTMUSIC
        btnKeyMusic.setOnClickListener(v -> injectMcuKey(209, 0));    // K_MUSIC

        btnFuelInfo.setOnClickListener(v -> startActivity(new Intent(this, FuelInfoActivity.class)));

        log("Ready. Package=" + getPackageName());
    }

    // ---------- Step 1: SourceInfo.onRequestPlayAudio ----------

    private void claimSource(String sourceKey) {
        try {
            Class<?> cls = Class.forName("android.sourceservice.SourceInfo");
            Method getInstance = cls.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance == null) {
                log("claimSource(" + sourceKey + "): getInstance() returned null");
                return;
            }
            // Try onRequestPlayAudio(String) first
            try {
                Method m = cls.getMethod("onRequestPlayAudio", String.class);
                m.invoke(instance, sourceKey);
                log("OK: onRequestPlayAudio(String=\"" + sourceKey + "\")");
                return;
            } catch (NoSuchMethodException e) {
                log("no onRequestPlayAudio(String) overload, trying Context variant");
            }
            // Fallback: onRequestPlayAudio(Context) - always reports OUR package
            Method m2 = cls.getMethod("onRequestPlayAudio", android.content.Context.class);
            m2.invoke(instance, this);
            log("OK: onRequestPlayAudio(Context) -> reports as " + getPackageName()
                    + " (requested sourceKey \"" + sourceKey + "\" ignored by this overload)");
        } catch (ClassNotFoundException e) {
            log("FAIL claimSource(" + sourceKey + "): android.sourceservice.SourceInfo not found on this ROM");
        } catch (InvocationTargetException e) {
            log("FAIL claimSource(" + sourceKey + "): threw " + e.getCause());
        } catch (Exception e) {
            log("FAIL claimSource(" + sourceKey + "): " + e);
        }
    }

    // ---------- Step 2: fake MediaSession with title/position/duration ----------

    private void startFakeSession() {
        if (mediaSession == null) {
            mediaSession = new MediaSessionCompat(this, TAG);
            mediaSession.setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        }
        String title = editTitle.getText().toString();
        if (title.trim().isEmpty()) title = "TEST_TRACK.mp3";

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "CAN Source Tester")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, fakeDurationMs)
                .build();
        mediaSession.setMetadata(metadata);
        mediaSession.setActive(true);

        fakePositionMs = 0;
        publishPlaybackState();

        sessionRunning = true;
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, 1000);

        log("Started fake MediaSession. title=\"" + title + "\" duration=" + formatMs(fakeDurationMs));
    }

    private void publishPlaybackState() {
        if (mediaSession == null) return;
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PLAYING, fakePositionMs, 1.0f)
                .build();
        mediaSession.setPlaybackState(state);
    }

    private void stopFakeSession() {
        sessionRunning = false;
        handler.removeCallbacks(tickRunnable);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        log("Stopped fake MediaSession.");
    }

    // ---------- Step 3: raw McuManager.injectKeyEvent ----------

    private void injectMcuKey(int keyCode, int extra) {
        try {
            Class<?> cls = Class.forName("android.carsource.McuManager");
            Method getInstance = cls.getMethod("getsInstance"); // note: OEM typo, not getInstance()
            Object instance = getInstance.invoke(null);
            if (instance == null) {
                log("injectMcuKey(" + keyCode + "): getsInstance() returned null");
                return;
            }
            Method inject = cls.getMethod("injectKeyEvent", int.class, int.class);
            inject.invoke(instance, keyCode, extra);
            log("OK: McuManager.injectKeyEvent(keyCode=" + keyCode + ", extra=" + extra + ")");
        } catch (ClassNotFoundException e) {
            log("FAIL injectMcuKey(" + keyCode + "): android.carsource.McuManager not found on this ROM");
        } catch (InvocationTargetException e) {
            log("FAIL injectMcuKey(" + keyCode + "): threw " + e.getCause());
        } catch (Exception e) {
            log("FAIL injectMcuKey(" + keyCode + "): " + e);
        }
    }

    // ---------- helpers ----------

    private String formatMs(int ms) {
        int totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        runOnUiThread(() -> {
            logView.append(msg + "\n");
        });
    }

    @Override
    protected void onDestroy() {
        stopFakeSession();
        super.onDestroy();
    }
}
