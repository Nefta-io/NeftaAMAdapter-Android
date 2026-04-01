package com.nefta.am;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.ads.mediation.nefta.NeftaAdapter;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.nefta.debug.DebugServer;
import com.nefta.sdk.InitConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private boolean _isSimulator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        InitUI();
        DebugServer.Init(this, getIntent());

        NeftaPlugin.EnableLogging(true);
        NeftaAdapter.InitWithAppId(this, "5713110509813760", (InitConfiguration config) -> {
            Log.i("NeftaPluginAM", "Should skip Nefta optimization: " + config._skipOptimization + " for: " + config._nuid);
        });

        new Thread(() -> {
            RequestConfiguration r = MobileAds.getRequestConfiguration().toBuilder()
                    /*.setTestDeviceIds(Arrays.asList(
                            "9429116F2099040F92F84E023664B484",
                            "40E5105E483D16020842051E0FFDCB4D",
                            "0D61331B015C8F81BCEEC7FD449CDEE7"))*/
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build();
            MobileAds.setRequestConfiguration(r);
        }).start();
    }

    private void InitUI() {
        TextView title = findViewById(R.id.title);
        title.setText("AdMob Integration "+ MobileAds.getVersion());
        title.setOnClickListener(v -> ToggleUI(!_isSimulator));
        ToggleUI(BuildConfig.IS_SIMULATOR);
    }

    private void ToggleUI(boolean isSimulator) {
        _isSimulator = isSimulator;

        findViewById(R.id.interstitialSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);
        findViewById(R.id.rewardedSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);

        findViewById(R.id.interstitial).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
        findViewById(R.id.rewarded).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
    }
}
