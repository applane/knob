package applane.knob;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.List;

public class MusicService extends MediaBrowserServiceCompat implements
        MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener
{
    private static final String CHANNEL_ID = "applane.knob.player";
    private static final int NOTIFICATION_ID = 1001;
    private NotificationManager notificationManager;
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private Song song = new Song();
    Bitmap largeIcon = null;

    private BroadcastReceiver noisyReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            pause();
        }
    };

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback()
    {
        @Override
        public void onPlay()
        {
            super.onPlay();

            if (mediaPlayer.getCurrentPosition() <= 0)
            {
                playCurrent();
                return;
            }
            play();
        }

        @Override // resets player on song folder change
        public void onCommand(String command, Bundle extras, ResultReceiver cb)
        {
            pause();
            if (mediaPlayer != null)
                mediaPlayer.reset();
            stopForeground( true );
        }

        @Override
        public void onSkipToNext()
        {
            super.onSkipToNext();
            if (!obtainAudioFocus()) return;
            skipToNext();
        }

        @Override
        public void onPause()
        {
            super.onPause();
            releaseAudioFocus();
            pause();
        }
    };

    void  skipToNext()
    {
        SongDir.nextSong();
        playCurrent();
    }

    void playCurrent()
    {
        if (!obtainAudioFocus()) return;

        try
        {
            Song s = SongDir.current();
            if (s == null) return;

            song = s;
            startService(new Intent(getApplicationContext(), MusicService.class));
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(song.file);
            mediaPlayer.prepare();
            setMediaSessionMetadata(s);
            play();
        }
        catch (Exception e)
        {
            Log.e("", "playCurrent: " + e.getMessage());
            releaseAudioFocus();
        }
    }

    private void play()
    {
        if (mediaPlayer != null)
        {
            if (!mediaPlayer.isPlaying())
            {
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                showPlayingNotification();
                mediaPlayer.start();
            }
            mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    private void pause()
    {
        if (mediaPlayer != null && mediaPlayer.isPlaying())
        {
            mediaPlayer.pause();
            setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            showPausedNotification();
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        SongDir.init(getApplicationContext());
        initMediaPlayer();
        initMediaSession();
        initNoisyReceiver();
        largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notify);
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent)
    {
        super.onTaskRemoved(rootIntent);
        if (!mediaPlayer.isPlaying())
            stopSelf();
    }

    private void initNoisyReceiver()
    {
        //Handles headphones coming unplugged. cannot be done through a manifest receiver
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyReceiver, filter);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        releaseAudioFocus();
        unregisterReceiver(noisyReceiver);
        mediaSession.release();
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        stopForeground( true );
    }

    private void initMediaPlayer()
    {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setVolume(1.0f, 1.0f);
        mediaPlayer.setOnCompletionListener(this);
    }

    private void showPlayingNotification()
    {
        showNotification(new NotificationCompat.Action(android.R.drawable.ic_media_pause,
                "Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_PLAY_PAUSE)));
    }

    private void showPausedNotification()
    {
        showNotification(new NotificationCompat.Action(android.R.drawable.ic_media_play,
                "Play", MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_PLAY_PAUSE)));
    }

    private void showNotification(NotificationCompat.Action action)
    {
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel();
            }

            NotificationCompat.Action skip =
                    new NotificationCompat.Action(android.R.drawable.ic_media_next,
                            "Skip", MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

            NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID);
            b.setContentTitle(song.artist)
                    .setContentText(song.title)
                    .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                    PlaybackStateCompat.ACTION_STOP))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(action)
                    .addAction(skip)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setShowActionsInCompactView(0).setMediaSession(mediaSession.getSessionToken()))
                    .setSmallIcon(R.drawable.play_bw);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            b.setContentIntent(contentIntent);

            startForeground(NOTIFICATION_ID, b.build());
        }
        catch(Exception e)
        {
            Log.e("", "showNotification: " + e.getMessage());
        }
    }

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(),
                MediaButtonReceiver.class);

        // media button
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                mediaButtonIntent, PendingIntent.FLAG_MUTABLE);

        // session
        mediaSession = new MediaSessionCompat(getApplicationContext(), "EP",
                mediaButtonReceiver, pendingIntent);
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setFlags( MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setActive(true);
    }

    private void setMediaPlaybackState(int state)
    {
        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        if( state == PlaybackStateCompat.STATE_PLAYING ) {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        } else {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY |  PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        }
        playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0);
        mediaSession.setPlaybackState(playbackstateBuilder.build());
    }

    private void setMediaSessionMetadata(Song song)
    {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
        b.putBitmap(MediaMetadataCompat. METADATA_KEY_ART, largeIcon);
        b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.artist);
        b.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.title);
        b.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.title);
        b.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1);
        b.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1);

        mediaSession.setMetadata(b.build());
    }

    private boolean obtainAudioFocus()
    {
        releaseAudioFocus();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return false;

        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        return result == AudioManager.AUDIOFOCUS_GAIN;
    }


    private void releaseAudioFocus()
    {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null)
                audioManager.abandonAudioFocus(this);
        } catch (Exception e) { }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        if(TextUtils.equals(clientPackageName, getPackageName())) {
            return new BrowserRoot(getString(R.string.app_name), null);
        }

        return null;
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch( focusChange ) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                pause();
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                if( mediaPlayer != null ) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
            }
            case AudioManager.AUDIOFOCUS_GAIN: {
                play();
                break;
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        SongDir.nextSong();
        playCurrent();
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(CHANNEL_ID,
                            getString(R.string.app_name),
                            NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription(getString(R.string.app_name));
            notificationChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
