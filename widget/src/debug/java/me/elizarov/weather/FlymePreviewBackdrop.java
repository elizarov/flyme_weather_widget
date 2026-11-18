package me.elizarov.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Local screenshot skin, used only by the emulator preview; never packaged in release.
 */
final class FlymePreviewBackdrop extends View {
    private final Bitmap screenshot;
    private final long now;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    FlymePreviewBackdrop(Context context, Bitmap screenshot, long now) {
        super(context);
        this.screenshot = screenshot;
        this.now = now;
        setContentDescription("Flyme Auto: подложка сохранённого экрана");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 5120f, getHeight() / 1600f);
        canvas.drawBitmap(screenshot, null, new RectF(0, 0, 5120, 1600), paint);
        // The reference capture has the old widget on a plain black wallpaper area.
        // Clear only its ink area; retain the surrounding wallpaper, bars and app cards.
        // Interpolate the clean wallpaper at both edges, retaining its subtle gradient.
        for (int y = 650; y < 980; y++) {
            int row = y * screenshot.getHeight() / 1600;
            int left = screenshot.getPixel(3740 * screenshot.getWidth() / 5120, row);
            int right = screenshot.getPixel(4590 * screenshot.getWidth() / 5120, row);
            paint.setShader(new android.graphics.LinearGradient(3740, 0, 4590, 0, left, right, android.graphics.Shader.TileMode.CLAMP));
            paint.setAlpha(Math.min(255, Math.min(y - 650, 979 - y) * 255 / 8));
            canvas.drawRect(3740, y, 4590, y + 1, paint);
        }
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setColor(screenshot.getPixel(165 * screenshot.getWidth() / 5120, 20 * screenshot.getHeight() / 1600));
        canvas.drawRect(160, 20, 312, 80, paint);
        paint.setColor(0xffe0e0e0);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(42);
        canvas.drawText(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date(now)), 180, 63, paint);
        canvas.restore();
    }
}
