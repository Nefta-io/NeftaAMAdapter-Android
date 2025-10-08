package com.nefta.am;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

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

class InterstitialWrapper extends FullScreenContentCallback implements OnPaidEventListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/2233679380";

    private AdRequest _dynamicRequest;
    private AdInsight _dynamicInsight;
    private InterstitialAd _dynamicInterstitial;
    private AdRequest _defaultRequest;
    private InterstitialAd _defaultInterstitial;
    private InterstitialAd _presentingInterstitial;

    private MainActivity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;
    private Handler _handler;

    private void StartLoading() {
        if (_dynamicRequest == null) {
            _dynamicInsight = null;
            GetInsightsAndLoad();
        }
        if (_defaultRequest == null) {
            LoadDefault();
        }
    }

    private void GetInsightsAndLoad() {
        if (_dynamicRequest != null || !_loadSwitch.isChecked()) {
            return;
        }

        _dynamicRequest = new AdRequest.Builder().build();

        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, _dynamicInsight, this::LoadWithInsights, 5);
    }

    private void LoadWithInsights(Insights insights) {
        _dynamicInsight = insights._interstitial;
        if (_dynamicInsight != null && _dynamicInsight._adUnit != null) {
            final String recommendedAdUnitId = _dynamicInsight._adUnit;
            Log("Loading Dynamic " + recommendedAdUnitId);
            NeftaAdapter.OnExternalMediationRequestWithInsight(_dynamicInsight, _dynamicRequest, recommendedAdUnitId);
            InterstitialAd.load(_activity, recommendedAdUnitId, _dynamicRequest,
                    new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd ad) {
                            NeftaAdapter.OnExternalMediationRequestLoaded(ad, _dynamicRequest);

                            Log("onAdLoaded Dynamic " + recommendedAdUnitId);

                            _dynamicInsight = null;
                            _dynamicInterstitial = ad;

                            ad.setFullScreenContentCallback(InterstitialWrapper.this);
                            ad.setOnPaidEventListener(InterstitialWrapper.this);

                            UpdateShowButton();
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                            NeftaAdapter.OnExternalMediationRequestFailed(_dynamicRequest, adError);

                            Log("onAdFailedToLoad Dynamic " + adError);

                            _dynamicRequest = null;

                            _handler.postDelayed(() -> {
                                GetInsightsAndLoad();
                            }, 5000);
                        }
                    });
        } else {
            _dynamicRequest = null;
        }
    }

    private void LoadDefault() {
        if (_defaultRequest != null || !_loadSwitch.isChecked()) {
            return;
        }

        Log("Loading Default " + DefaultAdUnitId);

        _defaultRequest = new AdRequest.Builder().build();
        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Interstitial, _defaultRequest, DefaultAdUnitId);
        InterstitialAd.load(_activity, DefaultAdUnitId, _defaultRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        NeftaAdapter.OnExternalMediationRequestLoaded(ad, _defaultRequest);

                        Log("onAdLoaded Default " + ad.getAdUnitId());

                        _defaultInterstitial = ad;

                        ad.setFullScreenContentCallback(InterstitialWrapper.this);
                        ad.setOnPaidEventListener(InterstitialWrapper.this);

                        UpdateShowButton();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        NeftaAdapter.OnExternalMediationRequestFailed(_defaultRequest, loadAdError);

                        Log("onAdFailedToLoad Default " + loadAdError.getMessage());

                        _defaultRequest = null;

                        _handler.postDelayed(() -> {
                            LoadDefault();
                        }, 5000);
                    }
                });
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(_presentingInterstitial, adValue);

        Log("onPaidEvent "+ adValue);
    }

    @Override
    public void onAdClicked() {
        NeftaAdapter.OnExternalMediationClick(_presentingInterstitial);

        Log("onAdClicked");
    }

    public InterstitialWrapper(MainActivity activity, Switch loadSwitch, Button showButton, TextView status) {
        _activity = activity;
        _loadSwitch = loadSwitch;
        _showButton = showButton;
        _status = status;

        _handler = new Handler(Looper.getMainLooper());

        _loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    StartLoading();
                } else {
                    _dynamicInsight = null;
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(_dynamicInterstitial != null) {
                    _dynamicInterstitial.show(_activity);
                    _presentingInterstitial = _dynamicInterstitial;
                    _dynamicInterstitial = null;
                    _dynamicRequest = null;
                } else if (_defaultInterstitial != null) {
                    _defaultInterstitial.show(_activity);
                    _presentingInterstitial = _defaultInterstitial;
                    _defaultInterstitial = null;
                    _defaultRequest = null;
                }
                UpdateShowButton();
            }
        });
        _showButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
       Log("onAdFailedToShowFullScreenContent "+ adError);
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
    public void onAdDismissedFullScreenContent() {
        Log("onAdDismissedFullScreenContent");

        _presentingInterstitial = null;

        // start new cycle
        if (_loadSwitch.isChecked()) {
            StartLoading();
        }
    }

    private void UpdateShowButton() {
        _showButton.setEnabled(_dynamicInterstitial != null || _defaultInterstitial != null);
    }

    private void Log(String log) {
        log = "Interstitial " + log;
        _status.setText(log);
        Log.i("NeftaPluginAM", log);
    }
}

