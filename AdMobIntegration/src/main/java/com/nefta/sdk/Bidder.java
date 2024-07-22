package com.nefta.sdk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class Bidder {

    interface IOnBidInternal {
        void Invoke(Placement placement, BidResponse bid);
    }

    private final String _impressionId = "1";
    private RestHelper _restHelper;

    private NeftaPlugin.Info _info;
    private NeftaPlugin.State _state;

    private IOnBidInternal _onBid;

    public void Init(NeftaPlugin.Info info, NeftaPlugin.State state, RestHelper restHelper, IOnBidInternal onBid) {
        _info = info;
        _state = state;
        _restHelper = restHelper;
        _onBid = onBid;
    }

    public void Bid(final Placement placement) {
        JSONObject requestObject = new JSONObject();
        try {
            requestObject.put("id", UUID.randomUUID().toString());
            requestObject.put("at", 1);

            JSONObject impression = new JSONObject();
            impression.put("id", _impressionId);
            impression.put("tagid", placement._id);
            JSONArray impressions = new JSONArray();
            impressions.put(impression);

            if (placement._customParameters != null) {
                JSONObject customParameters = null;
                for (Map.Entry<String, Object> parameter : placement._customParameters.entrySet()) {
                    String key = parameter.getKey();
                    if ("bidfloor".equals(key)) {
                        impression.put("bidfloor", parameter.getValue());
                    } else {
                        if (customParameters == null) {
                            JSONObject ext = new JSONObject();
                            JSONObject nefta = new JSONObject();
                            customParameters = new JSONObject();
                            nefta.put("custom_parameters", customParameters);
                            ext.put("nefta", nefta);
                            impression.put("ext", ext);
                        }
                        customParameters.put(parameter.getKey(), parameter.getValue());
                    }
                }
            }

            requestObject.put("imp", impressions);

            JSONObject application = new JSONObject();
            application.put("ver", _info._bundleVersion);
            requestObject.put("app", application);

            requestObject.put("device", NeftaPlugin.GetDeviceData(_info));

            JSONObject ext = new JSONObject();
            JSONObject nefta = new JSONObject();
            nefta.put("nuid", _state._nuid);
            if (_info._publisherUserId != null && _info._publisherUserId.length() > 0) {
                nefta.put("acuid", _info._publisherUserId);
            }
            nefta.put("sid", _state._sessionId);
            nefta.put("sdk_version", NeftaPlugin.Version);
            ext.put("nefta", nefta);
            requestObject.put("ext", ext);
        } catch (JSONException e) {
            NeftaPlugin.NLogI(  "Error creating bid request: "+ e.getMessage());
            _onBid.Invoke(placement, null);
            return;
        }

        _restHelper.MakePostRequest(_info._restUrl + "/bidder/bid", requestObject, placement, this::OnBidResponse);
    }

    private void OnBidResponse(final String response, Placement placement, int responseCode) {
        BidResponse bid = null;
        if (responseCode == 204) {
            _onBid.Invoke((Placement) placement, bid);
            NeftaPlugin.NLogI("No fill");
            return;
        }

        if (response != null) {
            try {
                JSONObject responseJson = new JSONObject(response);
                JSONArray seatBids = responseJson.getJSONArray("seatbid");
                for (int i = 0; i < seatBids.length(); i++) {
                    JSONObject seatBid = seatBids.getJSONObject(i);
                    JSONArray bids = seatBid.getJSONArray("bid");
                    for (int b = 0; b < bids.length(); b++) {
                        JSONObject jsonBid = bids.getJSONObject(i);
                        String impressionId = jsonBid.getString("impid");
                        if (!_impressionId.equals(impressionId)) {
                            continue;
                        }

                        bid = new BidResponse();
                        bid._id = jsonBid.getString("id");
                        bid._impressionId = impressionId;
                        bid._price = (float) jsonBid.getDouble("price");
                        String nurl = jsonBid.optString("nurl");
                        if (nurl.length() > 0) {
                            bid._impressionTrackingUrls.add(nurl);
                        }
                        String burl = jsonBid.optString("burl");
                        if (burl.length() > 0) {
                            bid._impressionTrackingUrls.add(burl);
                        }
                        bid._adMarkup = jsonBid.optString("adm");
                        bid._width = jsonBid.getInt("w");
                        bid._height = jsonBid.getInt("h");
                        bid._campaignId = jsonBid.optString("cid");
                        bid._creativeId = jsonBid.optString("crid");

                        JSONObject ext = jsonBid.optJSONObject("ext");
                        if (ext != null) {
                            JSONObject nefta = ext.optJSONObject("nefta");
                            if (nefta != null) {
                                bid._redirectClickUrl = nefta.optString("redirect_click_url");
                                bid._adMarkupType = BidResponse.AdMarkupTypes.FromString(nefta.optString("adm_content_type"));
                                bid._minWatchTime = nefta.optInt("min_creative_skip_time");

                                JSONArray impressionUrls = nefta.optJSONArray("imp_tracking_urls");
                                if (impressionUrls != null) {
                                    int length = impressionUrls.length();
                                    for (int s = 0; s < length; s++) {
                                        String url = impressionUrls.getString(s);
                                        if (url == null || url.length() == 0) {
                                            continue;
                                        }
                                        bid._impressionTrackingUrls.add(url);
                                    }
                                }
                                JSONArray clickUrls = nefta.optJSONArray("click_tracking_urls");
                                if (clickUrls != null) {
                                    int length = clickUrls.length();
                                    for (int s = 0; s < length; s++) {
                                        String url = clickUrls.getString(s);
                                        if (url == null || url.length() == 0) {
                                            continue;
                                        }
                                        bid._clickTrackingUrls.add(url);
                                    }
                                }
                            }
                        }
                        if (bid._adMarkupType == null) {
                            int vastTagIndex = bid._adMarkup.indexOf("<VAST");
                            if (vastTagIndex != -1) {
                                bid._adMarkupType = BidResponse.AdMarkupTypes.VastXml;
                            } else {
                                bid._adMarkupType = BidResponse.AdMarkupTypes.HtmlRaw;
                            }
                        }
                    }

                    if (bid != null) {
                        break;
                    }
                }
            } catch (JSONException e) {
                NeftaPlugin.NLogI("Error parsing bid: "+ e.getMessage());
            }
        }

        if (bid != null && bid._adMarkupType == BidResponse.AdMarkupTypes.VastXml) {
            int adTagPosition = bid._adMarkup.indexOf("<VASTAdTagURI>");
            if (adTagPosition > 0) {
                int adTagUrlStartPosition = bid._adMarkup.indexOf("<![CDATA[", adTagPosition) + 9;
                int adTagUrlEndPosition = bid._adMarkup.indexOf("]]>", adTagUrlStartPosition);
                String url = bid._adMarkup.substring(adTagUrlStartPosition, adTagUrlEndPosition);
                placement._availableBid = bid;
                _restHelper.LoadVastWrapperTag(url, placement, this::OnVastWrapperResponse);
                return;
            }
        }
        _onBid.Invoke((Placement) placement, bid);
    }

    private void OnVastWrapperResponse(String response, Placement placement, int statusCode) {
        placement._availableBid._adTag = response;
        _onBid.Invoke((Placement) placement, placement._availableBid);
    }
}
