package applane.knob;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String READ_MEDIA_AUDIO = Manifest.permission.READ_MEDIA_AUDIO;
    private static final String POST_NOTIFY = Manifest.permission.POST_NOTIFICATIONS;
    private static final int READ_REG = 1;
    private MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    Button playBtn;
    Button skipBtn;
    TextView artist, title;
    ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        playBtn = findViewById(R.id.btnPlay);
        playBtn.setVisibility(View.GONE);
        skipBtn = findViewById(R.id.btnSkip);
        skipBtn.setVisibility(View.INVISIBLE);
        artist = findViewById(R.id.artist);
        artist.setText("");
        title = findViewById(R.id.title);
        artist.setText("");
        progress = findViewById(R.id.progress);
        registerCardDoubleTap();
        initAll();
        obtainMediaPermission();
    }

    public void onMusicFolderSelect() {
        if (!hasMediaPermission()) return;
        Intent intent = new Intent(this, SelectFolder.class);
        startActivityForResult(intent, 0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            resetPlayer();
            if (!SongDir.initCard(this, data.getStringExtra("card")))
                toast(R.string.no_music);
            stateChanged();
        }
    }

    public void onPlayClick(View view) {
        if (!permissionsObtained()) return;
        if (!SongDir.hasSongs()) toast(R.string.no_music);
        if (!initialized()) return;
        resume();
    }

    public void onSkipClick(View view) {
        if (!permissionsObtained()) return;
        if (!SongDir.hasSongs()) toast(R.string.no_music);
        if (!initialized()) return;
        skipToNext();
    }

    private void stateChanged() {
        showPlayButton();
        if (!initialized()) {
            title.setText("");
            artist.setText("");
            setPlayIcon();
            skipBtn.setVisibility(View.INVISIBLE);
            return;
        }

        Song song = SongDir.current();
        if (song != null) {
            title.setText(song.title);
            artist.setText(song.artist);
        } else {
            title.setText("");
            artist.setText("");
        }

        if (!isPlaying())
            setPlayIcon();
        else
            setPauseIcon();

        skipBtn.setVisibility(View.VISIBLE);
        playBtn.setVisibility(View.VISIBLE);
    }

    void setPlayIcon() {
        playBtn.setText("\ue037");
    }

    void setPauseIcon() {
        playBtn.setText("\ue034");
    }

    void showPlayButton() {
        progress.setVisibility(View.GONE);
        playBtn.setVisibility(View.VISIBLE);
    }

    void toast(int resId) {
        Toast t = Toast.makeText(this, getString(resId), Toast.LENGTH_LONG);
        t.show();
    }

    protected void play() {
        if (!initialized()) return;
        transport().play();
    }

    protected void resetPlayer() {
        if (!initialized()) return;
        mediaController.sendCommand("reset", null, null);
    }

    protected void pause() {
        if (!initialized()) return;
        transport().pause();
    }

    protected void skipToNext() {
        if (!initialized()) return;
        transport().skipToNext();
    }

    protected void resume() {
        if (!initialized()) return;

        if (isPlaying())
            pause();
        else
            play();
    }

    protected boolean isPlaying() {
        return state() == PlaybackStateCompat.STATE_PLAYING;
    }

    private void initAll() {
        if (initialized()) return;
        if (hasMediaPermission())
            initPlayer();
        else
            showPlayButton();
    }

    private boolean initialized() {
        return (SongDir.hasSongs() && mediaBrowser != null && mediaBrowser.isConnected() &&
                mediaController != null && mediaController.isSessionReady());
    }

    protected MediaControllerCompat.TransportControls transport() {
        MediaControllerCompat mc = MediaControllerCompat.getMediaController(this);
        return mc.getTransportControls();
    }

    protected int state() {
        if (!initialized()) return PlaybackStateCompat.STATE_NONE;
        PlaybackStateCompat ps = mediaController.getPlaybackState();
        return (ps != null) ? ps.getState() : PlaybackStateCompat.STATE_NONE;
    }

    private void initPlayer() {
        mediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MusicService.class),
                mediaBrowserCallback, getIntent().getExtras());
        mediaBrowser.connect();
    }

    private final MediaBrowserCompat.ConnectionCallback mediaBrowserCallback
            = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            super.onConnected();
            try {
                if (mediaController != null)
                    mediaController.unregisterCallback(mediaControllerCallback);

                mediaController = new MediaControllerCompat(MainActivity.this,
                        mediaBrowser.getSessionToken());
                mediaController.registerCallback(mediaControllerCallback);
                MediaControllerCompat.setMediaController(MainActivity.this, mediaController);
                stateChanged();
            } catch (Exception e) {
                Log.e("", "onConnected: " + e.getMessage());
            }
        }

        @Override
        public void onConnectionFailed() {
            super.onConnectionFailed();
            stateChanged();
        }

        @Override
        public void onConnectionSuspended() {
            super.onConnectionSuspended();
            stateChanged();
        }
    };

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);
                    stateChanged();
                }
            };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaBrowser != null) mediaBrowser.disconnect();
    }

    private boolean permissionsObtained() {
        if (!hasMediaPermission()) {
            obtainMediaPermission();
            return false;
        }
        return true;
    }

    private void obtainMediaPermission() {
        if (hasMediaPermission()) return;
        ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE,
                READ_MEDIA_AUDIO, POST_NOTIFY}, READ_REG);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != READ_REG) return;

        // media
        if (permissions.length >= 2 &&
                results[0] != PackageManager.PERMISSION_GRANTED &&
                results[1] != PackageManager.PERMISSION_GRANTED) {
            showAppSettingsDialog();
            return;
        }

        SongDir.init(this);
        initAll();

        // notify
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S &&
                permissions.length >= 3 &&
                results[2] != PackageManager.PERMISSION_GRANTED) {
            showAppSettingsDialog();
        }
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            return (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED);
        }

        return (ContextCompat.checkSelfPermission(this, READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED);
    }

    private GestureDetector gestureDetector;

    @SuppressLint("ClickableViewAccessibility")
    private void registerCardDoubleTap() {
        TextView cardSelect = findViewById(R.id.cardSelect);
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        onMusicFolderSelect();
                        return true;
                    }
                });

        cardSelect.setOnTouchListener((v, event) ->
        {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    public void showAppSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_required)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                    intent.setData(uri);
                    this.startActivity(intent);
                })
                .setCancelable(true)
                .show();
    }
}