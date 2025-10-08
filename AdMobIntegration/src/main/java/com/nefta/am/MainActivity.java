package com.nefta.am;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private TextView _interstitialStatus;
    private TextView _rewardedStatus;

    @Override
        protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        _interstitialStatus = findViewById(R.id.interstitialStatus);
        _rewardedStatus = findViewById(R.id.rewardedStatus);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            String override = intent.getStringExtra("override");
            if (override != null && override.length() > 2) {
                NeftaPlugin.SetOverride(override);
            }
        }

        NeftaPlugin.EnableLogging(true);
        NeftaPlugin.SetExtraParameter(NeftaPlugin.ExtParam_TestGroup, "split-am");
        NeftaPlugin plugin = NeftaPlugin.Init(this, "5713110509813760");

        new Thread(() -> {
            RequestConfiguration r = MobileAds.getRequestConfiguration().toBuilder()
                    .setTestDeviceIds(Arrays.asList(
                            "9429116F2099040F92F84E023664B484",
                            "40E5105E483D16020842051E0FFDCB4D",
                            "0D61331B015C8F81BCEEC7FD449CDEE7"))
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build();
            MobileAds.setRequestConfiguration(r);

            MobileAds.initialize(this, initializationStatus -> {
                _interstitialStatus.setText("Initialized");
                _rewardedStatus.setText("Initialized");
            });
        }).start();

        new InterstitialWrapper(this, findViewById(R.id.loadInterstitial), findViewById(R.id.showInterstitial), _interstitialStatus);
        new RewardedWrapper(this, findViewById(R.id.loadRewarded), findViewById(R.id.showRewarded), _rewardedStatus);
    }
}
