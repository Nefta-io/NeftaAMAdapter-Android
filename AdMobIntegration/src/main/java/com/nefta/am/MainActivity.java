package com.nefta.am;

import android.os.Bundle;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    @Override
        protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        DebugServer.Init(this, getIntent());

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
        }).start();
    }
}
