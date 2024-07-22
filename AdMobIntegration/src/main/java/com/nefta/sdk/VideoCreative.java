package com.nefta.sdk;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class VideoCreative extends Creative {

    private Future<?> _loadingTask;
    private int _duration;
    private int _currentVideoPosition;
    private MediaPlayer _mediaPlayer;

    ArrayList<String> _trackingEventsFirstQuartile;
    ArrayList<String> _trackingEventsMidPoint;
    ArrayList<String> _trackingEventsThirdQuartile;
    ArrayList<String> _trackingEventsComplete;
    ArrayList<String> _trackingEventsPause;
    ArrayList<String> _trackingEventsResume;
    ArrayList<String> _trackingEventsClose;
    int _durationInSeconds;
    boolean _isStreaming;
    String _hashCode;
    String _videoPath;
    boolean _isRewardAvailable;
    boolean _isVideoCompleted;

    static String GetVideoDirectory(Context context) {
        return context.getApplicationInfo().dataDir + "/video";
    }

    static void TryDeleteVideo(Context context, String videoHash) {
        NeftaPlugin.NLogI("Delete expired video with hash: " + videoHash);

        String oldVideoPath = GetVideoDirectory(context) + "/" + videoHash;
        File oldVideo = new File(oldVideoPath);
        if (oldVideo.exists()) {
            oldVideo.delete();
        }
    }

    VideoCreative(Placement placement, BidResponse bid) {
        super(placement, bid);
    }

    void Load(final Context context) {
        _placement._publisher.CalculateSize(this);

        _loadingTask = _placement._publisher.GetExecutor().submit(new Load(context, _placement));
    }

    @Override
    void Show() {
        _placement.ShowFullscreen(this);
        super.Show();
    }

    public void OnVideoSurfaceReady(SurfaceHolder surfaceHolder) {
        NeftaPlugin.NLogI("OnVideoSurfaceReady");
        _mediaPlayer = new MediaPlayer();
        _mediaPlayer.setDisplay(surfaceHolder);
        if (_placement._publisher._isMuted) {
            _mediaPlayer.setVolume(0f, 0f);
        }
        try {
            _mediaPlayer.setDataSource(_videoPath);
            _mediaPlayer.prepare();
        } catch (Exception e) {
            NeftaPlugin.NLogW("Error preparing video: "+ e.getMessage());
            _mediaPlayer.release();
            _mediaPlayer = null;
            _placement.NextCreative();
            return;
        }

        _mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                if (_currentVideoPosition > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && _currentVideoPosition < _duration) {
                        _mediaPlayer.seekTo(_currentVideoPosition, MediaPlayer.SEEK_CLOSEST);
                    } else {
                        _mediaPlayer.seekTo(_currentVideoPosition);
                    }
                    _mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                        @Override
                        public void onSeekComplete(MediaPlayer mediaPlayer) {
                            if (!_placement._rendererActivity._isCloseConfirmationShown) {
                                _mediaPlayer.start();
                            }
                        }
                    });
                } else {
                    _currentVideoPosition = 0;
                    if (!_placement._rendererActivity._isCloseConfirmationShown) {
                        _mediaPlayer.start();
                    }
                }
            }
        });

        _mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (_trackingEventsComplete != null) {
                    for (String url : _trackingEventsComplete) {
                        _placement._publisher._restHelper.MakeGetRequest(url);
                    }
                    _trackingEventsComplete = null;
                }

                _isVideoCompleted = true;
                if (_isRewardAvailable) {
                    NeftaPlugin.NLogI("OnReward " + _placement._type + "(" + _placement._id + ")");
                    if (_placement._publisher._plugin._callbackInterface != null) {
                        _placement._publisher._plugin._callbackInterface.IOnReward(_placement._id);
                    }
                    if (_placement._publisher._plugin.OnReward != null) {
                        _placement._publisher._plugin.OnReward.Invoke(_placement);
                    }
                    _isRewardAvailable = false;
                }

                _currentVideoPosition = _duration * 1000;
                _mediaPlayer.pause();

                _placement.SwitchToEndCardIfAvailable();
            }
        });

        _placement._rendererActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _duration = _mediaPlayer.getDuration();
    }

    void OnVideoSurfaceDestroyed(SurfaceHolder surfaceHolder) {
        ReleaseMediaPlayer();
    }

    float OnUpdate() {
        if (_placement == null || _placement._rendererActivity == null || _mediaPlayer == null) {
            return -1;
        }

        if (_isVideoCompleted) {
            return 1;
        }

        if (_currentVideoPosition < 0 || !_mediaPlayer.isPlaying()) {
            return -1;
        }

        if (_currentVideoPosition < _duration) {
            int position = _mediaPlayer.getCurrentPosition();
            if (position >= 0) {
                _currentVideoPosition = position;
            }
        }
        float normalizedProgress = (float) _currentVideoPosition / _duration;
        if (normalizedProgress >= 0.25 && _trackingEventsFirstQuartile != null) {
            for (String url : _trackingEventsFirstQuartile) {
                _placement._publisher._restHelper.MakeGetRequest(url);
            }
            _trackingEventsFirstQuartile = null;
        }
        if (normalizedProgress >= 0.5 && _trackingEventsMidPoint != null) {
            for (String url : _trackingEventsMidPoint) {
                _placement._publisher._restHelper.MakeGetRequest(url);
            }
            _trackingEventsMidPoint = null;
        }
        if (normalizedProgress >= 0.75 && _trackingEventsThirdQuartile != null) {
            for (String url : _trackingEventsThirdQuartile) {
                _placement._publisher._restHelper.MakeGetRequest(url);
            }
            _trackingEventsThirdQuartile = null;
        }

        return normalizedProgress;
    }

    @Override
    void OnPause(boolean paused) {
        if (_currentVideoPosition >= 0 && _mediaPlayer != null) {
            if (paused) {
                _placement._rendererActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (_mediaPlayer.isPlaying()) {
                    _mediaPlayer.pause();
                }
            } else {
                _placement._rendererActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (!_mediaPlayer.isPlaying() && _currentVideoPosition < _duration) {
                    _mediaPlayer.start();
                }
            }
        }
    }

    @Override
    void Dispose() {
        if (_loadingTask != null) {
            _loadingTask.cancel(true);
            _loadingTask = null;
        }

        ReleaseMediaPlayer();
        _currentVideoPosition = -1;

        if (_hashCode != null && _hashCode.length() > 0) {
            TryDeleteVideo(NeftaPlugin._context, _hashCode);
            if (_placement != null) {
                _placement._publisher.UpdateCachedVideoPrefs(false, _hashCode);
            }
            _hashCode = null;
        }

        if (_placement != null && _placement._rendererActivity != null) {
            _placement._rendererActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        _placement = null;
    }

    private void ReleaseMediaPlayer() {
        if (_mediaPlayer != null) {
            _mediaPlayer.stop();
            _mediaPlayer.setSurface(null);
            _mediaPlayer.release();
            _mediaPlayer = null;
        }
    }

    private class Load implements Runnable {
        private final Context _context;
        private final Placement _placement;

        public Load(Context context, final Placement placement) {
            _context = context;
            _placement = placement;
        }

        public void run() {
            try {
                String videoDirectoryPath = GetVideoDirectory(_context);
                File videoDirectory = new File(videoDirectoryPath);
                if (!videoDirectory.exists()) {
                    videoDirectory.mkdir();
                }

                _hashCode = String.valueOf(_adMarkup.hashCode());
                _videoPath = videoDirectoryPath + "/" + _hashCode;
                File videoFile = new File(_videoPath);
                if (videoFile.exists()) {
                    NeftaPlugin.NLogI("Rewarded video already exists: " + _videoPath);
                    _placement.OnCreativeLoadingEnd(null);
                    return;
                }

                URL request = new URL(_adMarkup);
                HttpURLConnection connection = (HttpURLConnection) request.openConnection();
                connection.setConnectTimeout(RestHelper._connectTimeoutMs);
                connection.setReadTimeout(RestHelper._readTimeoutMs);

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                InputStream inputStream = connection.getInputStream();
                OutputStream outputStream = new FileOutputStream(_videoPath);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                outputStream.close();
                connection.disconnect();
                NeftaPlugin.NLogI("Rewarded-video preloaded successfully: " + _videoPath);
                _placement._publisher.UpdateCachedVideoPrefs(true, _hashCode);
                _placement.OnCreativeLoadingEnd(null);
            } catch (Exception e) {
                final String error = e.getMessage();
                NeftaPlugin.NLogI("Exception preloading video: " + error);
                _placement.OnCreativeLoadingEnd(error);
            }
        }
    }


}
