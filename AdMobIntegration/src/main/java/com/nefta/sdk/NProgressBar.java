package com.nefta.sdk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class NProgressBar extends View {
    private final int _progressColor;
    private float _progress = -1;

    private final Paint _backgroundPaint;
    private final Paint _paint;

    public NProgressBar(Context context, int backgroundColor, int progressColor) {
        super(context);

        _backgroundPaint = new Paint();
        _backgroundPaint.setColor(backgroundColor);

        _progressColor = progressColor;
        _paint = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        int progressWidth = (int) (width * _progress);

        canvas.drawRect(0, 0, width, height, _backgroundPaint);
        canvas.drawRect(0, 0, progressWidth, height, _paint);
    }

    public void SetProgress(float progress) {
        if (_progress != progress) {
            _progress = progress;

            _paint.setColor(_progress >= 1 ? Color.GREEN : _progressColor);

            invalidate();
        }
    }
}
