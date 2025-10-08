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
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

public class RewardedWrapper extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/8841990779";

    private AdRequest _dynamicRequest;
    private AdInsight _dynamicInsight;
    private RewardedAd _dynamicRewarded;
    private AdRequest _defaultRequest;
    private RewardedAd _defaultRewarded;
    private RewardedAd _presentingRewarded;

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

        NeftaPlugin._instance.GetInsights(Insights.REWARDED, _dynamicInsight, this::LoadWithInsights, 5);
    }

    private void LoadWithInsights(Insights insights) {
        _dynamicInsight = insights._rewarded;
        if (_dynamicInsight != null && _dynamicInsight._adUnit != null) {
            String recommendedAdUnitId = _dynamicInsight._adUnit;
            Log("Loading Dynamic " + recommendedAdUnitId);
            NeftaAdapter.OnExternalMediationRequestWithInsight(_dynamicInsight, _dynamicRequest, recommendedAdUnitId);
            RewardedAd.load(_activity, _dynamicInsight._adUnit, _dynamicRequest,
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            NeftaAdapter.OnExternalMediationRequestLoaded(ad, _dynamicRequest);

                            Log("onAdLoaded Dynamic " + recommendedAdUnitId);

                            _dynamicInsight = null;
                            _dynamicRewarded = ad;

                            ad.setFullScreenContentCallback(RewardedWrapper.this);
                            ad.setOnPaidEventListener(RewardedWrapper.this);

                            UpdateShowButton();
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            NeftaAdapter.OnExternalMediationRequestFailed(_dynamicRequest, loadAdError);

                            Log("onAdFailedToLoad Dynamic " + loadAdError.getMessage());

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
        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Rewarded, _defaultRequest, DefaultAdUnitId);
        RewardedAd.load(_activity, DefaultAdUnitId, _defaultRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        NeftaAdapter.OnExternalMediationRequestLoaded(ad, _defaultRequest);

                        Log("onAdLoaded Default " + ad.getAdUnitId());

                        _defaultRewarded = ad;

                        ad.setFullScreenContentCallback(RewardedWrapper.this);
                        ad.setOnPaidEventListener(RewardedWrapper.this);

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
        NeftaAdapter.OnExternalMediationImpression(_presentingRewarded, adValue);

        Log("onPaidEvent "+ adValue);
    }

    @Override
    public void onAdClicked() {
        NeftaAdapter.OnExternalMediationClick(_presentingRewarded);

        Log("onAdClicked");
    }

    public RewardedWrapper(MainActivity activity, Switch loadButton, Button showButton, TextView status) {
        _activity = activity;
        _loadSwitch = loadButton;
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
                if (_dynamicRewarded != null) {
                    _dynamicRewarded.show(_activity, RewardedWrapper.this);
                    _presentingRewarded = _dynamicRewarded;
                    _dynamicRewarded = null;
                    _dynamicRequest = null;
                } else if (_defaultRewarded != null) {
                    _defaultRewarded.show(_activity, RewardedWrapper.this);
                    _presentingRewarded = _defaultRewarded;
                    _defaultRewarded = null;
                    _defaultRequest = null;
                }
                UpdateShowButton();
            }
        });
        _showButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        Log("onAdFailedToShowFullScreenContent");
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

        _presentingRewarded = null;

        // start new cycle
        if (_loadSwitch.isChecked()) {
            StartLoading();
        }
    }

    @Override
    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        Log("onUserEarnedReward " + rewardItem);
    }

    private void UpdateShowButton() {
        _showButton.setEnabled(_dynamicRewarded != null || _defaultRewarded != null);
    }

    private void Log(String log) {
        log = "Rewarded " + log;
        _status.setText(log);
        Log.i("NeftaPluginAM", log);
    }
}