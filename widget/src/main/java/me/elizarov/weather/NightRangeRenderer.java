package me.elizarov.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * Render small date glyphs by their ink bounds, independent of OEM line metrics.
 */
final class NightRangeRenderer {
    private NightRangeRenderer() {
    }

    static Bitmap render(Context context, String start, String end, float labelWidth, float temperatureWidth, int color) {
        Bitmap bitmap = Bitmap.createBitmap(Math.round(labelWidth + 12 + temperatureWidth), 40, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create(Typeface.create("Flyme Sans VF", Typeface.NORMAL), 400, false));
        paint.setTextSize(24);
        paint.setColor(color);
        float centerX = labelWidth / 2, centerY = 20;
        Rect bounds = new Rect();
        // Long month-boundary dates fit around the same centered arrow.
        paint.getTextBounds(start, 0, start.length(), bounds);
        float fit = Math.min(1, (centerX - 16) / Math.max(1, bounds.width()));
        paint.getTextBounds(end, 0, end.length(), bounds);
        fit = Math.min(fit, (bitmap.getWidth() - centerX - 16) / Math.max(1, bounds.width()));
        paint.setTextSize(24 * fit);
        paint.getTextBounds(start, 0, start.length(), bounds);
        canvas.drawText(start, centerX - 13 - bounds.right, centerY - (bounds.top + bounds.bottom) / 2f, paint);
        paint.getTextBounds(end, 0, end.length(), bounds);
        canvas.drawText(end, centerX + 13 - bounds.left, centerY - (bounds.top + bounds.bottom) / 2f, paint);
        Drawable arrow = context.getDrawable(R.drawable.ic_date_arrow).mutate();
        arrow.setTint(color);
        int x = Math.round(centerX);
        arrow.setBounds(x - 9, 11, x + 9, 29);
        arrow.draw(canvas);
        return bitmap;
    }
}
