package com.nefta.sdk;

import android.view.View;
import android.widget.FrameLayout;

public class PlacementView {
    public View _view;
    public FrameLayout.LayoutParams _layoutParams;

    public PlacementView(View view, FrameLayout.LayoutParams layoutParams) {
        _view = view;
        _layoutParams = layoutParams;
    }
}
