package com.nefta.sdk;

public interface CallbackInterface {
    void IOnReady(String configuration);

    void IOnBid(String pId, float price);

    void IOnLoadStart(String pId);

    void IOnLoadFail(String pId, String error);

    void IOnLoad(String pId, int width, int height);

    void IOnShow(String pId);

    void IOnClick(String pId);

    void IOnReward(String pId);

    void IOnClose(String pId);
}
