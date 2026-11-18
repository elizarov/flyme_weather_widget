package me.elizarov.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Raster captions keep the arrow aligned to digit ink across RemoteViews processes/fonts.
 */
final class CaptionRenderer {
    private CaptionRenderer() {
    }

    static Bitmap render(Context context, String caption, int color) {
        Bitmap bitmap = Bitmap.createBitmap(264, 44, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(28);
        paint.setTypeface(Typeface.create(context.getString(R.string.font_display_permission), Typeface.NORMAL));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = (bitmap.getHeight() - metrics.descent - metrics.ascent) / 2f;
        int arrow = caption.indexOf('→');
        if (arrow < 0) {
            canvas.drawText(caption, (bitmap.getWidth() - paint.measureText(caption)) / 2f, baseline, paint);
            return bitmap;
        }
        String left = caption.substring(0, arrow), right = caption.substring(arrow + 1);
        float arrowWidth = 18, gap = 4;
        float width = paint.measureText(left) + paint.measureText(right) + arrowWidth + 2 * gap;
        float x = (bitmap.getWidth() - width) / 2f;
        canvas.drawText(left, x, baseline, paint);
        x += paint.measureText(left) + gap;
        String rangeDigits = left.substring(left.lastIndexOf(' ') + 1) + right;
        Path numeralOutlines = new Path();
        paint.getTextPath(rangeDigits, 0, rangeDigits.length(), 0, baseline, numeralOutlines);
        RectF numeralBounds = new RectF();
        numeralOutlines.computeBounds(numeralBounds, true);
        // Float glyph bounds avoid both font-line padding and rounded integer bounds.
        // The shaft and arrowhead are symmetric about this measured center; no Y offset.
        float center = numeralBounds.centerY();
        Path path = new Path();
        path.moveTo(x, center);
        path.lineTo(x + arrowWidth, center);
        path.moveTo(x + arrowWidth - 5, center - 5);
        path.lineTo(x + arrowWidth, center);
        path.lineTo(x + arrowWidth - 5, center + 5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(right, x + arrowWidth + gap, baseline, paint);
        return bitmap;
    }
}
