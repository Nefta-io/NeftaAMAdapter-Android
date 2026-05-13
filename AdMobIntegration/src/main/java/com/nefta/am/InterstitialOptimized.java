package com.nefta.am;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.ads.mediation.nefta.NeftaAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

public class InterstitialOptimized implements Interstitial {

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready,
        Shown
    }

    private class Track extends FullScreenContentCallback implements OnPaidEventListener {
        public final String _adUnitId;
        public AdInsight _insight;
        public AdRequest _request;
        public InterstitialAd _interstitial;
        public double _floorPrice;
        public State _state = State.Idle;

        public InterstitialAdLoadCallback _loadCallbacks;

        public Track(String adUnitId) {
            _adUnitId = adUnitId;
            _loadCallbacks = new InterstitialAdLoadCallback() {

                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    _interstitial = ad;
                    NeftaAdapter.OnExternalMediationRequestLoaded(_interstitial, _request);

                    Log("Loaded " + _adUnitId);

                    ad.setFullScreenContentCallback(Track.this);
                    ad.setOnPaidEventListener(Track.this);

                    _insight = null;
                    _request = null;
                    _state = State.Ready;

                    OnTrackLoad(true);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    NeftaAdapter.OnExternalMediationRequestFailed(_request, error);

                    Log("onAdFailedToLoad " + _adUnitId + ": " + error);

                    _request = null;
                    _interstitial = null;
                    AfterLoadFail();
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
            }, (int)(NeftaAdapter.GetRetryDelayInSeconds(_insight) * 1000));
        }

        public boolean TryShow(Activity activity) {
            _state = State.Shown;
            _request = null;
            _floorPrice = 0;

            _interstitial.show(activity);
            return true;
        }

        @Override
        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            Log("onAdFailedToShowFullScreenContent "+ adError);

            _state = State.Idle;
            _interstitial = null;
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
            NeftaAdapter.OnExternalMediationImpression(_interstitial, adValue);

            Log("onPaidEvent "+ adValue.getValueMicros());
        }

        @Override
        public void onAdClicked() {
            NeftaAdapter.OnExternalMediationClick(_interstitial);

            Log("onAdClicked");
        }

        @Override
        public void onAdDismissedFullScreenContent() {
            Log("onAdDismissedFullScreenContent");

            _state = State.Idle;
            _interstitial = null;
            RetryLoadTracks();
        }
    }

    private Track _trackA;
    private Track _trackB;
    private boolean _isFirstResponseReceived = false;

    private InterstitialUi _ui;
    private Handler _handler;

    public void Init(InterstitialUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _trackA = new Track(AdUnitA);
        _trackB = new Track(AdUnitB);
    }

    public void Load() {
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

        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, track._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._interstitial != null) {
                track._insight = insights._interstitial;
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

                NeftaAdapter.OnExternalMediationRequestWithInsight(track._insight, track._request, track._adUnitId);

                Log("Loading " + track._adUnitId + " as Optimized with " + mediationGroup);
                InterstitialAd.load(_ui.Activity, track._adUnitId, track._request, track._loadCallbacks);
            } else {
                track.AfterLoadFail();
            }
        });
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        track._floorPrice = 0;
        track._request = new AdRequest.Builder().build();

        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Interstitial, track._request, track._adUnitId);

        Log("Loading " + track._adUnitId + " as Default");
        InterstitialAd.load(_ui.Activity, track._adUnitId, track._request, track._loadCallbacks);
    }

    public void Show() {
        boolean isShown = false;
        if (_trackA._state == State.Ready) {
            if (_trackB._state == State.Ready && _trackB._floorPrice > _trackA._floorPrice) {
                isShown = _trackB.TryShow(_ui.Activity);
            }
            if (!isShown) {
                isShown = _trackA.TryShow(_ui.Activity);
            }
        }
        if (!isShown && _trackB._state == State.Ready) {
            _trackB.TryShow(_ui.Activity);
        }
        UpdateShowButton();
    }

    public void RetryLoadTracks() {
        if (_ui.IsAutoLoad) {
            Load();
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
        _ui.SetAvailability(_trackA._state == State.Ready || _trackB._state == State.Ready);
    }

    private void Log(String log) {
        _ui.Log(log);
    }
}
