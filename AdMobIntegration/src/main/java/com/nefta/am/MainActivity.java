package com.nefta.am;

import android.os.Bundle;

import com.google.android.gms.ads.MobileAds;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private BannerWrapper _bannerWrapper;
    private InterstitialWrapper _interstitialWrapper;
    private RewardedVideoWrapper _rewardedVideoWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_main );

      NeftaPlugin.Init(this, "5643649824063488");

      new Thread(() -> {
          MobileAds.initialize(this, initializationStatus -> {});
      }).start();

      _bannerWrapper = new BannerWrapper(this, findViewById(R.id.bannerView), findViewById(R.id.showBanner), findViewById(R.id.closeBanner));
      _interstitialWrapper = new InterstitialWrapper(this, findViewById(R.id.loadInterstitial), findViewById(R.id.showInterstitial));
      _rewardedVideoWrapper = new RewardedVideoWrapper(this, findViewById(R.id.loadRewardedVideo), findViewById(R.id.showRewardedVideo));
    }
}
