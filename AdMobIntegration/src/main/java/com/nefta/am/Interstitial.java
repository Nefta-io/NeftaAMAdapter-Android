package com.nefta.am;

public interface Interstitial {
    public final String AdUnitA = "ca-app-pub-1193175835908241/2233679380";
    public final String AdUnitB = "ca-app-pub-1193175835908241/4300296872";

    public void Init(InterstitialUi ui);
    public void Load();
    public void Show();
}
