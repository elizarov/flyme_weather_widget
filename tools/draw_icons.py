"""Original monochrome vector icons: 64-unit grid, round 3-unit strokes."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'widget/src/main/res/drawable'
cloud = 'M18,43 C10,43 8,31 16,27 C15,16 33,11 39,23 C50,19 58,27 55,35 C54,40 50,43 45,43 Z'
sun = 'M30,13 A10,10 0,1 1,29.99,13 M30,1 L30,6 M30,40 L30,45 M8,23 L13,23 M47,23 L52,23 M14,7 L18,11 M42,35 L46,39 M14,39 L18,35 M42,11 L46,7'
icons = {
 'weather_cloud': [cloud],
 'weather_sun': [sun],
 'weather_moon': ['M43,9 C20,11 16,38 38,47 C16,55 2,34 13,17 C20,7 31,4 43,9 Z'],
 'weather_partly': ['M20,12 A10,10 0,1 1,8,25 M19,1 L19,5 M3,8 L6,11 M35,8 L32,11', cloud],
 'weather_rain': [cloud, 'M21,50 L17,58 M33,50 L29,58 M45,50 L41,58'],
 'weather_snow': [cloud, 'M31,48 L31,62 M25,51 L37,59 M25,59 L37,51'],
 'weather_sleet': [cloud, 'M19,51 L16,58 M42,48 L42,62 M36,51 L48,59 M36,59 L48,51'],
 'weather_thunder': [cloud, 'M34,45 L26,55 L34,55 L29,63'],
 'weather_fog': ['M12,20 L52,20 M6,30 L58,30 M12,40 L52,40 M19,50 L45,50'],
 'weather_unknown': ['M32,8 A24,24 0,1 1,31.99,8 M25,25 C25,15 44,18 39,28 L32,35 L32,39 M32,47 L32,48'],
 'ic_settings': ['M26,8 L38,8 L40,15 L46,19 L53,17 L59,27 L54,32 L54,39 L59,44 L53,54 L46,52 L40,56 L38,63 L26,63 L24,56 L18,52 L11,54 L5,44 L10,39 L10,32 L5,27 L11,17 L18,19 L24,15 Z', 'M32,25 A10,10 0,1 1,31.99,25'],
 'ic_back': ['M38,12 L18,32 L38,52 M18,32 L56,32'],
}
for name, paths in icons.items():
    # Gear is centred at y=35.5; translate to the common optical centre.
    group_start, group_end = ('<group android:translateY="-3.5">', '</group>') if name == 'ic_settings' else ('', '')
    body = '\n'.join(f'    <path android:fillColor="@android:color/transparent" android:strokeColor="#ffffffff" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{path}"/>' for path in paths)
    (ROOT/(name+'.xml')).write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="64dp" android:height="64dp" android:viewportWidth="64" android:viewportHeight="64">\n{group_start}\n{body}\n{group_end}\n</vector>\n')
print('Drew', len(icons), 'vector icons.')
