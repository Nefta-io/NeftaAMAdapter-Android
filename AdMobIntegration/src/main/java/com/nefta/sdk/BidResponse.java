package com.nefta.sdk;

import java.util.ArrayList;
import java.util.List;

public class BidResponse {
    public enum AdMarkupTypes {
        VastXml,
        HtmlRaw,
        HtmlLink,
        ImageLink;

        public static AdMarkupTypes FromString(String type) {
            switch (type) {
                case "vast_xml":
                    return AdMarkupTypes.VastXml;
                case "html_link":
                    return AdMarkupTypes.HtmlLink;
                case "image_link":
                    return AdMarkupTypes.ImageLink;
            }
            return AdMarkupTypes.HtmlRaw;
        }
    }

    public String _id;
    public String _impressionId;
    public float _price;
    public List<String> _impressionTrackingUrls = new ArrayList<>();
    public List<String> _clickTrackingUrls = new ArrayList<>();
    public List<String> _errorUrls;
    public String _redirectClickUrl;
    public String _adMarkup;
    public String _adTag;
    public AdMarkupTypes _adMarkupType;
    public int _minWatchTime;
    public String _campaignId;
    public String _creativeId;
    public int _width;
    public int _height;
}
