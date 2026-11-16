package me.elizarov.weather;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Debug-only gallery of the actual widget vectors, rendered by Android.
 */
public final class IconGalleryActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff1c1c1c);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(1800, 1180, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        position.topMargin = 140;
        root.addView(content, position);
        content.addView(label("Иконки погоды", 56));
        int[] icons = {R.drawable.weather_sun, R.drawable.weather_moon, R.drawable.weather_partly,
                R.drawable.weather_cloud, R.drawable.weather_rain, R.drawable.weather_snow,
                R.drawable.weather_sleet, R.drawable.weather_thunder, R.drawable.weather_fog, R.drawable.weather_unknown};
        String[] names = {"Ясно", "Ясная ночь", "Переменная\nоблачность", "Облачно", "Дождь",
                "Снег", "Дождь со снегом", "Гроза", "Туман", "Нет данных"};
        for (int start = 0; start < icons.length; start += 5) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = start; i < start + 5; i++)
                row.addView(tile(icons[i], names[i]), new LinearLayout.LayoutParams(0, 280, 1));
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, 280);
            line.topMargin = 36;
            content.addView(row, line);
        }
        TextView title = label("Интерфейс", 38);
        LinearLayout.LayoutParams heading = new LinearLayout.LayoutParams(-1, 80);
        heading.topMargin = 36;
        content.addView(title, heading);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(tile(R.drawable.ic_settings, "Настройки"), new LinearLayout.LayoutParams(360, 240));
        controls.addView(tile(R.drawable.ic_back, "Назад"), new LinearLayout.LayoutParams(360, 240));
        controls.addView(tile(R.drawable.ic_date_arrow, "Переход даты"), new LinearLayout.LayoutParams(360, 240));
        content.addView(controls);
        setContentView(root);
    }

    private LinearLayout tile(int resource, String name) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setColorFilter(0xfff2f2f2);
        tile.addView(icon, new LinearLayout.LayoutParams(128, 128));
        TextView title = label(name, 32);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(-1, 90);
        caption.topMargin = 22;
        tile.addView(title, caption);
        return tile;
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xfff2f2f2);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
        return view;
    }
}
