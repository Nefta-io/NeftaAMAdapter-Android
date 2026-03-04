package com.nefta.am;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.ads.mediation.nefta.NeftaAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

public class RewardedSim extends TableLayout {
    private final String AdUnitA = "AdUnit-A";
    private final String AdUnitB = "AdUnit-B";

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready,
        Shown
    }

    public class Track extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener {
        public final String _adUnitId;
        public AdInsight _insight;
        public AdRequest _request;
        public RewardedAd _rewarded;
        public double _floorPrice;
        public State _state = State.Idle;

        public RewardedAdLoadCallback _loadCallbacks;
        public int _loadSelection;

        public Track(String adUnitId) {
            _adUnitId = adUnitId;
            _loadCallbacks = new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    _handler.post(() -> {
                        _rewarded = ad;
                        NeftaAdapter.OnExternalMediationRequestLoaded(_rewarded, _request);

                        Log("Loaded " + _adUnitId);

                        ad.setFullScreenContentCallback(Track.this);
                        ad.setOnPaidEventListener(Track.this);

                        _insight = null;
                        _request = null;
                        _state = State.Ready;

                        OnTrackLoad(true);
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    _handler.post(() -> {
                        NeftaAdapter.OnExternalMediationRequestFailed(_request, error);

                        Log("onAdFailedToLoad Dynamic " + error);

                        _request = null;
                        _rewarded = null;
                        AfterLoadFail();
                    });
                }
            };
        }

        public void AfterLoadFail() {
            RetryLoad();

            OnTrackLoad(false);
        }

        private void RetryLoad() {
            _handler.postDelayed(() -> {
                _state = State.Idle;
                RetryLoadTracks();
            }, 5000);
        }

        public boolean TryShow(Activity activity) {
            _state = State.Shown;
            _request = null;
            _floorPrice = 0;

            _rewarded.show(activity, this);
            return true;
        }

        @Override
        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            Log("onAdFailedToShowFullScreenContent");

            _state = State.Idle;
            _rewarded = null;
            RetryLoadTracks();
        }

        @Override
        public void onAdImpression() {
            Log("onAdImpression");
        }

        @Override
        public void onAdShowedFullScreenContent() {
            Log("onAdShowedFullScreenContent");
        }

        @Override
        public void onPaidEvent(@NonNull AdValue adValue) {
            NeftaAdapter.OnExternalMediationImpression(_rewarded, adValue);

            Log("onPaidEvent "+ adValue);
        }

        @Override
        public void onAdClicked() {
            NeftaAdapter.OnExternalMediationClick(_rewarded);

            Log("onAdClicked");
        }

        @Override
        public void onAdDismissedFullScreenContent() {
            Log("onAdDismissedFullScreenContent");

            _state = State.Idle;
            _rewarded = null;
            RetryLoadTracks();
        }

        @Override
        public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
            Log("onUserEarnedReward " + rewardItem);
        }
    }

    public Track _trackA;
    public Track _trackB;
    private boolean _isFirstResponseReceived = false;

    private Activity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;

    public TextView _aStatus;
    private Button _aFill2;
    private Button _aFill1;
    private Button _aNoFill;
    private Button _aOther;

    public TextView _bStatus;
    private Button _bFill2;
    private Button _bFill1;
    private Button _bNoFill;
    private Button _bOther;

    private Handler _handler;
    public static RewardedSim Instance;

    private void LoadTracks() {
        LoadTrack(_trackA, _trackB._state);
        LoadTrack(_trackB, _trackA._state);
    }

    private void LoadTrack(Track track, State otherState) {
        if (track._state == State.Idle) {
            if (otherState == State.LoadingWithInsights || otherState == State.Shown) {
                if (_isFirstResponseReceived) {
                    LoadDefault(track);
                }
            } else {
                GetInsightsAndLoad(track);
            }
        }
    }

    private void GetInsightsAndLoad(Track track) {
        track._state = State.LoadingWithInsights;

        NeftaPlugin._instance.GetInsights(Insights.REWARDED, track._insight, (Insights insights) -> {
            Log("LoadWithInsights " + insights);
            if (insights._rewarded != null) {
                track._insight = insights._rewarded;
                track._floorPrice = track._insight._floorPrice;

                // map floorPrice to your AdMob Pro mediation group configuration
                // sample KVP mapping:
                String mediationGroup = "low";
                if (track._floorPrice > 100)
                {
                    mediationGroup = "high";
                }
                else if (track._floorPrice > 50)
                {
                    mediationGroup = "medium";
                }
                Bundle extras = new Bundle();
                extras.putString("mediation group key", mediationGroup);

                track._request = new AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter.class, extras).build();

                NeftaAdapter.OnExternalMediationRequestWithInsight(insights._rewarded, track._request, track._adUnitId);

                Log("Loading " + track._adUnitId + " as Optimized with " + mediationGroup);
                SRewardedAd.load(_activity, track._adUnitId, track._request, track._loadCallbacks);
            } else {
                track.AfterLoadFail();
            }
        }, 5);
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        track._floorPrice = 0;
        track._request = new AdRequest.Builder().build();

        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Rewarded, track._request, track._adUnitId);

        Log("Loading " + track._adUnitId + " as Default");
        SRewardedAd.load(_activity, track._adUnitId, track._request, track._loadCallbacks);
    }

    public RewardedSim(Context context) {
        super(context);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    public RewardedSim(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Instance = this;

        _loadSwitch = findViewById(R.id.rewardedSim_load);
        _showButton = findViewById(R.id.rewardedSim_show);
        _status = findViewById(R.id.rewardedSim_status);

        _handler = new Handler(Looper.getMainLooper());

        _trackA = new Track(AdUnitA);
        _trackB = new Track(AdUnitB);

        _loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    LoadTracks();
                }
            }
        });
        _showButton.setOnClickListener(view -> {
            boolean isShown = false;
            if (_trackA._state == State.Ready) {
                if (_trackB._state == State.Ready && _trackB._floorPrice > _trackA._floorPrice) {
                    isShown = _trackB.TryShow(_activity);
                }
                if (!isShown) {
                    isShown = _trackA.TryShow(_activity);
                }
            }
            if (!isShown && _trackB._state == State.Ready) {
                _trackB.TryShow(_activity);
            }
            UpdateShowButton();
        });

        _showButton.setEnabled(false);

        _aStatus = findViewById(R.id.rewardedSim_statusA);
        _aFill2 = findViewById(R.id.rewardedSim_fill2A);
        _aFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, true));
        _aFill1 = findViewById(R.id.rewardedSim_fill1A);
        _aFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, false));
        _aNoFill = findViewById(R.id.rewardedSim_noFillA);
        _aNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 2));
        _aOther = findViewById(R.id.rewardedSim_OtherA);
        _aOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 0));
        ToggleTrackA(false, true);

        _bStatus = findViewById(R.id.rewardedSim_statusB);
        _bFill2 = findViewById(R.id.rewardedSim_fill2B);
        _bFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, true));
        _bFill1 = findViewById(R.id.rewardedSim_fill1B);
        _bFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, false));
        _bNoFill = findViewById(R.id.rewardedSim_noFillB);
        _bNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 2));
        _bOther = findViewById(R.id.rewardedSim_OtherB);
        _bOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 0));
        ToggleTrackB(false, true);
    }

    public void RetryLoadTracks() {
        if (_loadSwitch.isChecked()) {
            LoadTracks();
        }
    }

    public void OnTrackLoad(boolean success) {
        if (success) {
            UpdateShowButton();
        }

        _isFirstResponseReceived = true;
        RetryLoadTracks();
    }

    private void UpdateShowButton() {
        _showButton.setEnabled(_trackA._state == State.Ready || _trackB._state == State.Ready);
    }

    private void Log(String log) {
        _status.setText(log);
        Log.i("NeftaPluginAM", "Rewarded " + log);
    }

    public void ToggleTrackA(boolean on, boolean refresh) {
        _aFill2.setEnabled(on);
        _aFill1.setEnabled(on);
        _aNoFill.setEnabled(on);
        _aOther.setEnabled(on);

        if (refresh) {
            _aFill2.setBackgroundResource(R.drawable.button);
            _aFill1.setBackgroundResource(R.drawable.button);
            _aNoFill.setBackgroundResource(R.drawable.button);
            _aOther.setBackgroundResource(R.drawable.button);
        }

        _aFill2.refreshDrawableState();
        _aFill1.refreshDrawableState();
        _aNoFill.refreshDrawableState();
        _aOther.refreshDrawableState();
    }

    public void ToggleTrackB(boolean on, boolean refresh) {
        _bFill2.setEnabled(on);
        _bFill1.setEnabled(on);
        _bNoFill.setEnabled(on);
        _bOther.setEnabled(on);

        if (refresh) {
            _bFill2.setBackgroundResource(R.drawable.button);
            _bFill1.setBackgroundResource(R.drawable.button);
            _bNoFill.setBackgroundResource(R.drawable.button);
            _bOther.setBackgroundResource(R.drawable.button);
        }

        _bFill2.refreshDrawableState();
        _bFill1.refreshDrawableState();
        _bNoFill.refreshDrawableState();
        _bOther.refreshDrawableState();
    }

    private void SimOnAdLoadedEvent(Track request, boolean isHigh) {
        if (request._rewarded != null && ((SRewardedAd)request._rewarded)._hasFill) {
            ((SRewardedAd)request._rewarded)._hasFill = false;

            if (request == _trackA) {
                if (isHigh) {
                    _aFill2.setBackgroundResource(R.drawable.button);
                    _aFill2.setEnabled(false);
                } else {
                    _aFill1.setBackgroundResource(R.drawable.button);
                    _aFill1.setEnabled(false);
                }
            } else {
                if (isHigh) {
                    _bFill2.setBackgroundResource(R.drawable.button);
                    _bFill2.setEnabled(false);
                } else {
                    _bFill1.setBackgroundResource(R.drawable.button);
                    _bFill1.setEnabled(false);
                }
            }
            return;
        }

        if (request == _trackA) {
            ToggleTrackA(false, false);
            if (isHigh) {
                _aFill2.setBackgroundResource(R.drawable.button_fill);
                _aFill2.setEnabled(true);

                request._loadSelection = 1;
            } else {
                _aFill1.setBackgroundResource(R.drawable.button_fill);
                _aFill1.setEnabled(true);

                request._loadSelection = 2;
            }
            _aStatus.setText(request._adUnitId + " loaded");
        } else {
            ToggleTrackB(false, false);
            if (isHigh) {
                _bFill2.setBackgroundResource(R.drawable.button_fill);
                _bFill2.setEnabled(true);

                request._loadSelection = 1;
            } else {
                _bFill1.setBackgroundResource(R.drawable.button_fill);
                _bFill1.setEnabled(true);

                request._loadSelection = 2;
            }
            _bStatus.setText(request._adUnitId + " loaded");
        }
    }

    private void SimOnAdFailedEvent(Track request, int status) {
        if (request == _trackA) {
            if (status == 2) {
                _aNoFill.setBackgroundResource(R.drawable.button_no);

                request._loadSelection = 3;
            } else {
                _aOther.setBackgroundResource(R.drawable.button_no);

                request._loadSelection = 4;
            }
            ToggleTrackA(false, false);
            _aStatus.setText(request._adUnitId + " failed");
        } else {
            if (status == 2) {
                _bNoFill.setBackgroundResource(R.drawable.button_no);

                request._loadSelection = 3;
            } else {
                _bOther.setBackgroundResource(R.drawable.button_no);

                request._loadSelection = 4;
            }
            _bStatus.setText(request._adUnitId + " failed");
            ToggleTrackB(false, false);
        }
    }
}