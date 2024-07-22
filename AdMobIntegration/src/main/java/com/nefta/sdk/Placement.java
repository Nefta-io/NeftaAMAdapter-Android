package com.nefta.sdk;

import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

public class Placement {

    public enum Position {
        TOP,
        BOTTOM,
        CENTER;

        public static Position FromInt(int position) {
            switch (position) {
                case 0:
                    return Position.TOP;
                case 1:
                    return Position.BOTTOM;
                case 2:
                    return Position.CENTER;
            }
            return null;
        }

        public int ToInt() {
            switch (this) {
                case TOP:
                    return 0;
                case BOTTOM:
                    return 1;
                case CENTER:
                    return 2;
            }
            return 0;
        }
    }

    public enum Types {
        Banner,
        Interstitial,
        RewardedVideo;

        public static String ToString(Types type) {
            switch (type) {
                case Banner:
                    return "0";
                case Interstitial:
                    return "1";
                case RewardedVideo:
                    return "2";
            }
            return null;
        }

        public static Types FromInt(int type) {
            switch (type) {
                case 0:
                    return Types.Banner;
                case 1:
                    return Types.Interstitial;
                case 2:
                    return Types.RewardedVideo;
            }
            return null;
        }
    }

    public enum Modes {
        Manual,
        ScheduledBid,
        ScheduledLoad,
        Continuous;

        public static String ToString(Modes mode) {
            switch (mode) {
                case Manual:
                    return "0";
                case ScheduledBid:
                    return "1";
                case ScheduledLoad:
                    return "2";
                case Continuous:
                    return "3";
            }
            return null;
        }

        public static Modes FromInt(int mode) {
            switch (mode) {
                case 0:
                    return Modes.Manual;
                case 1:
                    return Modes.ScheduledBid;
                case 2:
                    return Modes.ScheduledLoad;
                case 3:
                    return Modes.Continuous;
            }
            return null;
        }
    }

    private long _preloadDelayForSubsequentCreatives = 1000;
    static long _placementContinuousDurationInMs = 30 * 1000;

    public final String _id;
    public final int _width;
    public final int _height;
    public Position _position;
    public final Types _type;
    public BidResponse _availableBid;
    public BidResponse _bufferBid;
    public BidResponse _renderedBid;
    public Publisher _publisher;

    RendererActivity _rendererActivity;
    public ArrayList<Creative> _bufferedCreatives;
    public Creative _bufferedCreative;
    public Creative _renderedCreative;
    public ArrayList<Creative> _nextCreatives;
    boolean _triggerNextCreativeOnLoad = false;
    HashMap<String, Object> _customParameters;

    public Modes _mode;
    public long _bidTime;
    public long _loadTime;
    public long _showTime;
    public boolean _isHidden;
    public boolean _isManualPosition;

    public boolean IsBidding() {
        return _bidTime > 0;
    }

    public boolean IsLoading() {
        return _loadTime > 0;
    }

    public boolean IsShowing() { return _showTime > 0 && !_isHidden; }

    public boolean CanLoad() {
        return !IsLoading();
    }

    public boolean CanShow() {
        if(_bufferBid == null || IsLoading() || _isHidden) {
            return false;
        }
        if (_mode == Modes.Continuous && _showTime > 0 && System.currentTimeMillis() - _showTime < _placementContinuousDurationInMs) {
            return false;
        }
        return true;
    }

    Placement(final String id, final int width, final int height, final Placement.Position position, final Types type, Publisher publisher) {
        _id = id;
        _width = width;
        _height = height;
        _position = position;
        _type = type;

        _publisher = publisher;

        _bidTime = 0;
        _loadTime = 0;
        _mode = Modes.Manual;
    }

    void OnBidStart() {
        _bidTime = System.currentTimeMillis();
    }

    void OnBid(BidResponse bid) {
        if (bid != null) {
            NeftaPlugin.NLogI("OnBid " + _type +"("+ _id + "): " + bid._creativeId + " " + bid._price);
        } else {
            NeftaPlugin.NLogI("OnBid " + _type +"("+ _id + "): fail -1");
        }

        _bidTime = 0;
        if (_mode == Modes.ScheduledBid) {
            _mode = Modes.Manual;
        }
        if (bid == null) {
            if (_mode == Modes.ScheduledLoad) {
                _mode = Modes.Manual;
            }
        }
        _availableBid = bid;
    }

    void Load() {
        NeftaPlugin.NLogI("Load " + _type + "(" + _id + "): "+ _availableBid._creativeId);

        _bufferBid = _availableBid;
        _availableBid = null;
        _loadTime = System.currentTimeMillis();

        _bufferedCreatives = new ArrayList<>();
        if (_bufferBid._adMarkupType == BidResponse.AdMarkupTypes.VastXml) {
            final String videoError = ParseVast(_bufferBid);
            if (videoError != null) {
                _publisher.OnLoadingEnd(this, videoError);
            } else {
                VideoCreative videoCreative = (VideoCreative) _bufferedCreatives.get(0);
                _bufferedCreative = videoCreative;
                _bufferedCreatives.remove(0);
                videoCreative.Load(NeftaPlugin._context);
                videoCreative._isRewardAvailable = _type == Placement.Types.RewardedVideo;
            }
        } else {
            if (_bufferBid._adMarkupType == BidResponse.AdMarkupTypes.ImageLink) {
                _bufferedCreative = new StaticCreative(this, _bufferBid);
            } else {
                _bufferedCreative = new WebCreative(this, _bufferBid);
            }
            _bufferedCreative.Load();
        }
    }

    void OnLoadFail(String error) {
        String creativeId = null;
        if (_bufferBid != null) {
            creativeId = _bufferBid._creativeId;
            if (_bufferBid._adMarkupType == BidResponse.AdMarkupTypes.VastXml) {
                TryReportVideoError(_bufferBid, VastErrorCodes.UnableToFetchResources);
            }
        }
        NeftaPlugin.NLogI("OnLoadingEnd fail " + _type +"("+ _id +"): "+ creativeId + " " + error);

        _loadTime = 0;
        _bufferBid = null;
        _bufferedCreatives = null;
        _bufferedCreative = null;
    }

    void OnLoad() {
        String creativeId = null;
        if (_bufferBid != null) {
            creativeId = _bufferBid._creativeId;
        }
        NeftaPlugin.NLogI("OnLoad " + _type +"("+ _id +"): "+ creativeId);

        _loadTime = 0;
        if (_mode == Modes.ScheduledLoad) {
            _mode = Modes.Manual;
        }
    }

    void Show() {
        String creativeId = null;
        if (_bufferBid != null) {
            creativeId = _bufferBid._creativeId;
        }
        NeftaPlugin.NLogI("Show "+ _type + "("+ _id + "): "+ creativeId);

        _showTime = System.currentTimeMillis();
        _isHidden = false;
        _renderedBid = _bufferBid;
        _bufferBid = null;

        _nextCreatives = _bufferedCreatives;
        _bufferedCreatives = null;

        if (_renderedCreative != null) {
            _renderedCreative.Dispose();
            _renderedCreative = null;
        }

        NextCreative();

        if (_renderedBid._impressionTrackingUrls != null) {
            for (String url : _renderedBid._impressionTrackingUrls) {
                _publisher._restHelper.MakeGetRequest(url);
            }
            _renderedBid._impressionTrackingUrls = null;
        }
    }

    void SwitchToEndCardIfAvailable() {
        if (_bufferedCreative != null || (_nextCreatives != null && _nextCreatives.size() > 0)) {
            NextCreative();
        }
    }

    void NextCreative() {
        if (_bufferedCreative != null) {
            if (_bufferedCreative._isLoaded) {
                _renderedCreative = _bufferedCreative;
                _bufferedCreative = null;

                _renderedCreative.Show();
            }
        } else {
            if (_nextCreatives != null && _nextCreatives.size() > 0) {
                _triggerNextCreativeOnLoad = true;
                PreloadNext();
            } else {
                _publisher.CloseMain(_id, true);
            }
        }
    }

    void OnUpdate(long now) {
        if (now - _showTime > _preloadDelayForSubsequentCreatives && _bufferedCreatives == null && _nextCreatives != null && _nextCreatives.size() > 0) {
            PreloadNext();
        }

        if (_mode == Placement.Modes.Continuous) {
            if (_type == Placement.Types.Banner && CanShow()) {
                _publisher.Show(_id);
            }
        }
        if (IsLoading() && now - _loadTime > RestHelper._readTimeoutMs) {
            if (_bufferedCreatives != null) {
                for (Creative creative : _bufferedCreatives) {
                    creative.Dispose();
                }
                _bufferedCreatives = null;
            }
            if (_bufferedCreative != null) {
                _bufferedCreative.Dispose();
                _bufferedCreative = null;
            }

            OnCreativeLoadingEnd("timeout");
        }
    }

    void OnClick(String customRedirectUrl) {
        String creativeId = null;
        if (_renderedBid != null) {
            creativeId = _renderedBid._creativeId;
        }
        NeftaPlugin.NLogI("OnClick " + _type + "("+ _id + "): "+ creativeId);

        if (_renderedBid._clickTrackingUrls != null) {
            for (String url : _renderedBid._clickTrackingUrls) {
                _publisher._restHelper.MakeGetRequest(url);
            }
            _renderedBid._clickTrackingUrls = null;
        }

        if (_renderedCreative._trackingClickUrls != null) {
            for (String url : _renderedCreative._trackingClickUrls) {
                _publisher._restHelper.MakeGetRequest(url);
            }
            _renderedCreative._trackingClickUrls = null;
        }

        String redirectUrl = _renderedCreative._redirectClickUrl;
        if (customRedirectUrl != null) {
            redirectUrl = customRedirectUrl;
        }
        if (redirectUrl != null && redirectUrl.length() > 0) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                NeftaPlugin._context.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                NeftaPlugin.NLogW("Error redirecting to: "+ redirectUrl);
            }
        }
    }

    void OnPause(boolean paused) {
        if (_renderedCreative != null) {
            _renderedCreative.OnPause(paused);
        }
    }

    void Hide(boolean hide) {
        _isHidden = hide;

        if (_type == Types.Banner && _renderedCreative != null) {
            _renderedCreative.Hide(hide);
        }
    }

    void ShowFullscreen(Creative creative) {
        if (_rendererActivity != null) {
            _rendererActivity.SetCreative(creative);
        } else {
            _publisher.ShowFullscreen(creative);
        }
    }

    private void PreloadNext() {
        _bufferedCreative = _nextCreatives.get(0);
        _nextCreatives.remove(0);
        _bufferedCreative.Load();
    }

    void Close() {
        String creativeId = null;
        if (_renderedBid != null) {
            creativeId = _renderedBid._creativeId;
        }
        NeftaPlugin.NLogI("Close " + _type + "(" + _id + "): "+ creativeId);

        _mode = Modes.Manual;
        _isHidden = false;
        _showTime = 0;
        _renderedBid = null;
        _triggerNextCreativeOnLoad = false;

        if (_rendererActivity != null) {
            _rendererActivity.Finish();
            _rendererActivity = null;
        }

        if (_renderedCreative != null) {
            _renderedCreative.Dispose();
            _renderedCreative = null;
        }

        _nextCreatives = null;
    }

    void OnCreativeLoadingEnd(String error) {
        if (_rendererActivity != null) {
            if (error != null) {
                _bufferedCreative = null;
                if (_nextCreatives != null && _nextCreatives.size() > 0) {
                    PreloadNext();
                }
            } else {
                _bufferedCreative._isLoaded = true;
                if (_triggerNextCreativeOnLoad) {
                    _triggerNextCreativeOnLoad = false;
                    if (Looper.myLooper() == _publisher._handler.getLooper()) {
                        NextCreative();
                    } else {
                        _publisher._handler.post(new Runnable() {
                            @Override
                            public void run() {
                                NextCreative();
                            }
                        });
                    }
                }
            }
        } else {
            if (error == null) {
                if (_bufferedCreative != null) {
                    _bufferedCreative._isLoaded = true;
                } else {
                    NeftaPlugin.NLogW("Placement onCreativeLoading already expired");
                    return;
                }
            } else {
                _bufferedCreative = null;
            }
            _publisher.OnLoadingEnd(this, error);
        }
    }

    public enum VastErrorCodes {
        ParsingError("100"),
        GeneralLinearError("400"),
        UnableToFetchResources("502"),
        UndefinedError("900");

        private final String _code;

        private VastErrorCodes(String code) {
            _code = code;
        }

        public String GetCode() {
            return _code;
        }
    }

    public void TryReportVideoError(BidResponse bid, VastErrorCodes error) {
        if (bid._errorUrls != null) {
            for (String errorUrl : bid._errorUrls) {
                String url = errorUrl.replace("[ERRORCODE]", error.GetCode());
                _publisher._restHelper.MakeGetRequest(url);
            }
        }
    }

    private VideoCreative _parsingVideoCreative;
    private XmlPullParserFactory _factory;

    private String ParseVast(BidResponse bid) {
        String error = null;
        try {
            _factory = XmlPullParserFactory.newInstance();

            ParseVastXml(bid._adMarkup, bid);
            bid._adMarkup = null;
        } catch (XmlPullParserException | IOException e) {
            TryReportVideoError(bid, VastErrorCodes.ParsingError);
            error = e.getMessage();
        }

        _parsingVideoCreative = null;
        _factory = null;

        if (_bufferedCreatives.size() == 0) {
            error = "no linear element";
            TryReportVideoError(bid, VastErrorCodes.GeneralLinearError);
        } else {
            NeftaPlugin.NLogI("Rewarded-video(" + _id + "): " + bid._creativeId + " vast parsed: " + _bufferedCreatives.size());
        }

        return error;
    }

    private void ParseVastXml(String xml, BidResponse bid) throws XmlPullParserException, IOException {
        Creative companionCreative = null;
        int companionWidth = 0;
        int companionHeight = 0;

        XmlPullParser parser = _factory.newPullParser();
        parser.setInput(new StringReader(xml));

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag;
            if (eventType == XmlPullParser.START_TAG) {
                tag = parser.getName();
                if (tag.equals("VASTAdTagURI") && bid._adTag != null) {
                    ParseVastXml(bid._adTag, bid);
                } else if (tag.equals("Linear")) {
                    if (_parsingVideoCreative == null) {
                        _parsingVideoCreative = new VideoCreative(this, bid);
                        _bufferedCreatives.add(_parsingVideoCreative);
                    }
                } else if (tag.equals("Tracking")) {
                    String eventName = parser.getAttributeValue(null, "event");
                    parser.next();
                    String trackingUrl = parser.getText();
                    if (trackingUrl == null || trackingUrl.length() == 0) {

                    } else if (eventName.equals("start")) {
                        _parsingVideoCreative._trackingEventsStart.add(trackingUrl);
                    } else if (eventName.equals("firstQuartile")) {
                        if (_parsingVideoCreative._trackingEventsFirstQuartile == null) {
                            _parsingVideoCreative._trackingEventsFirstQuartile = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsFirstQuartile.add(trackingUrl);
                    } else if (eventName.equals("midpoint")) {
                        if (_parsingVideoCreative._trackingEventsMidPoint == null) {
                            _parsingVideoCreative._trackingEventsMidPoint = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsMidPoint.add(trackingUrl);
                    } else if (eventName.equals("thirdQuartile")) {
                        if (_parsingVideoCreative._trackingEventsThirdQuartile == null) {
                            _parsingVideoCreative._trackingEventsThirdQuartile = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsThirdQuartile.add(trackingUrl);
                    } else if (eventName.equals("complete")) {
                        if (_parsingVideoCreative._trackingEventsComplete == null) {
                            _parsingVideoCreative._trackingEventsComplete = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsComplete.add(trackingUrl);
                    } else if (eventName.equals("pause")) {
                        if (_parsingVideoCreative._trackingEventsPause == null) {
                            _parsingVideoCreative._trackingEventsPause = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsPause.add(trackingUrl);
                    } else if (eventName.equals("resume")) {
                        if (_parsingVideoCreative._trackingEventsResume == null) {
                            _parsingVideoCreative._trackingEventsResume = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsResume.add(trackingUrl);
                    } else if (eventName.equals("close")) {
                        if (_parsingVideoCreative._trackingEventsClose == null) {
                            _parsingVideoCreative._trackingEventsClose = new ArrayList<>();
                        }
                        _parsingVideoCreative._trackingEventsClose.add(trackingUrl);
                    } else if (eventName.equals("creativeView")) {
                        if (companionCreative != null) {
                            companionCreative._trackingEventsStart.add(trackingUrl);
                        }
                    }
                } else if (tag.equals("Impression")) {
                    parser.next();
                    String impressionUrl = parser.getText();
                    if (impressionUrl != null && impressionUrl.length() > 0) {
                        bid._impressionTrackingUrls.add(impressionUrl);
                    }
                } else if (tag.equals("Error")) {
                    if (bid._errorUrls == null) {
                        bid._errorUrls = new ArrayList<>();
                    }
                    parser.next();
                    String errorUrl = parser.getText();
                    if (errorUrl != null && errorUrl.length() > 0) {
                        bid._errorUrls.add(errorUrl);
                    }
                } else if (tag.equals("Duration")) {
                    parser.next();
                    String[] durationSegments = parser.getText().split(":");
                    _parsingVideoCreative._durationInSeconds = Integer.parseInt(durationSegments[0]) * 3600;
                    _parsingVideoCreative._durationInSeconds += Integer.parseInt(durationSegments[1]) * 60;
                    _parsingVideoCreative._durationInSeconds += Integer.parseInt(durationSegments[2]);
                } else if (tag.equals("ClickThrough")) {
                    parser.next();
                    String url = parser.getText();
                    if (url != null && url.length() > 0) {
                        _parsingVideoCreative._redirectClickUrl = url;
                    }
                } else if (tag.equals("ClickTracking")) {
                    parser.next();
                    String url = parser.getText();
                    if (url != null && url.length() > 0) {
                        _parsingVideoCreative._trackingClickUrls.add(url);
                    }
                } else if (tag.equals("MediaFile")) {
                    _parsingVideoCreative._isStreaming = parser.getAttributeValue(null, "delivery").equals("streaming");
                    _parsingVideoCreative._width = Integer.parseInt(parser.getAttributeValue(null, "width"));
                    _parsingVideoCreative._height = Integer.parseInt(parser.getAttributeValue(null, "height"));
                    parser.next();
                    _parsingVideoCreative._adMarkup = parser.getText();
                } else if (tag.equals("Companion")) {
                    companionWidth = Integer.parseInt(parser.getAttributeValue(null, "width"));
                    companionHeight = Integer.parseInt(parser.getAttributeValue(null, "height"));
                } else if (tag.equals("StaticResource")) {
                    parser.next();
                    String url = parser.getText();
                    companionCreative = new StaticCreative(this, companionWidth, companionHeight, url);
                    _bufferedCreatives.add(companionCreative);
                } else if (tag.equals("HTMLResource")) {
                    parser.next();
                    String html = parser.getText();
                    companionCreative = new WebCreative(this, companionWidth, companionHeight, html, false);
                    _bufferedCreatives.add(companionCreative);
                } else if (tag.equals("IFrameResource")) {
                    parser.next();
                    String url = parser.getText();
                    companionCreative = new WebCreative(this, companionWidth, companionHeight, url, true);
                    _bufferedCreatives.add(companionCreative);
                } else if (tag.equals("CompanionClickThrough")) {
                    parser.next();
                    String url = parser.getText();
                    if (companionCreative != null && url != null && url.length() > 0) {
                        companionCreative._redirectClickUrl = url;
                    }
                } else if (tag.equals("CompanionClickTracking")) {
                    parser.next();
                    String url = parser.getText();
                    if (companionCreative != null && url != null && url.length() > 0) {
                        companionCreative._trackingClickUrls.add(url);
                    }
                }
            }
            eventType = parser.next();
        }
    }
}
