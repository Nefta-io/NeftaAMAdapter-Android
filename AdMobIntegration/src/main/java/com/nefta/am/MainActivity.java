package com.nefta.am;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final String _tag = "NeftaPluginAM";

    private BannerWrapper _bannerWrapper;
    private InterstitialWrapper _interstitialWrapper;
    private RewardedWrapper _rewardedVideoWrapper;
    private TextView _status;

    @Override
        protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        NeftaPlugin.EnableLogging(true);
        NeftaPlugin plugin = NeftaPlugin.Init(this, "5713110509813760");
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            String override = intent.getStringExtra("override");
            if (override != null && override.length() > 2) {
                plugin.SetOverride(override);
            }
        }

        new Thread(() -> {
            RequestConfiguration r = MobileAds.getRequestConfiguration().toBuilder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build();
            MobileAds.setRequestConfiguration(r);

            MobileAds.initialize(this, initializationStatus -> {
                Log("Initialized");
            });
        }).start();

        _status = findViewById(R.id.status);

        _bannerWrapper = new BannerWrapper(this, findViewById(R.id.bannerView), findViewById(R.id.showBanner), findViewById(R.id.closeBanner));
        _interstitialWrapper = new InterstitialWrapper(this, findViewById(R.id.loadInterstitial), findViewById(R.id.showInterstitial));
        _rewardedVideoWrapper = new RewardedWrapper(this, findViewById(R.id.loadRewardedVideo), findViewById(R.id.showRewardedVideo));
    }

    void Log(String log) {
        _status.setText(log);
        Log.i(_tag, log);
    }
}
