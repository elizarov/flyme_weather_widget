# WeatherWidget

Пассажирский виджет даты, времени и погоды для Flyme Auto. Проверено на `LYNK FlymeAuto 900 2.3.1.5`.
Дата и часы следуют часовому поясу Android. Прогноз Гидрометцентра показывает сегодняшний день и ближайшую ночь; город, резервный город и автолокация выбираются через шестерёнку. Каталог городов загружается при открытии настроек, если кэш старше суток. Ключ API и учётная запись не нужны.

Приложение заменяет штатный AppWidget через отдельный resource overlay (RRO).

Overlay перекрывает ресурс `com.flyme.auto.launcher:xml/widget_profiles`, из которого лаунчер читает состав и размещение виджетов. Скрипт извлекает этот XML из локального APK лаунчера и в пассажирской секции погоды (`displayType="psd"`, `widgetDisplay="weather"`) заменяет `widgetPackageName` с `com.flyme.auto.weather` на `me.elizarov.weather`, а `widgetClassName` с `com.flyme.auto.weather.module.ipcservice.appwidget.OrdinaryAppWidgetProvider` на `me.elizarov.weather.WeatherWidgetProvider`. Остальные записи и параметры сохраняются. В результате лаунчер сам размещает наш AppWidget в штатном погодном блоке; APK лаунчера не изменяется.

## Сборка

Нужны JDK 17+, Python 3.9+, Android SDK Platform 36 и Build Tools 36.0.0. Gradle wrapper включён в проект. Укажите `JAVA_HOME` и `ANDROID_HOME`, если они ещё не настроены; в скриптах на macOS используется SDK из `~/Library/Android/sdk`.

Из корня проекта, после подключения ADB:

```sh
export ANDROID_SERIAL=DEVICE_IP:5555
python3 tools/pull-assets.py --apks-only
./tools/build.sh
```

Получатся `widget/build/outputs/apk/release/widget-release.apk` и `overlay/build/weather-overlay.apk`.
Скрипт собирает подписанные release APK, инструментальные тесты для release и запускает Lint. Приложение и overlay подписываются одним постоянным release-ключом.

При первом запуске создаются `signing/release.keystore` и `signing/release.properties`. Последующие сборки используют тот же ключ. Сохраните резервную копию всего каталога `signing/`: он содержит закрытый ключ и пароли, исключён из Git и нужен для выпуска совместимых обновлений. Отдельная подготовка ключа: `python3 tools/signing.py init`.

Для последующих сборок только приложения: `./gradlew :widget:assembleRelease`.

## Локальные ресурсы машины

`pull-assets.py --apks-only` получает пути пакетов через Android Package Manager и скачивает `AutoLauncher.apk` и `AutoWeather.apk` в `local-assets/`. Можно указать устройство явно: `--serial DEVICE_SERIAL`. Скрипт только читает файлы через ADB; root для скачивания не нужен.

При сборке `prepare-assets.py` использует `aapt2` из Android SDK: извлекает основу макета даты/часов и разделитель из APK погоды, добавляет наш интерфейс прогноза и создаёт overlay из профиля виджетов APK лаунчера. В нём меняются пакет и класс погодного AppWidget; остальные записи сохраняются. Сначала используются ожидаемые имена ресурсов, затем подходящие варианты из таблицы ресурсов APK. Результат записывается в `local-assets/generated/`. Проверок SHA или номера прошивки нет; ошибка возвращается, если необходимые ресурсы извлечь не удалось.

Если APK уже сохранены локально, их можно передать без подключения к машине:

```sh
./tools/build.sh /path/to/AutoLauncher.apk /path/to/AutoWeather.apk
```

Без флагов `python3 tools/pull-assets.py` скачивает и APK, и шрифты. Весь каталог `local-assets/`, включая оригинальные XML, шрифты, APK и сгенерированные ресурсы, исключён из Git. В репозитории хранятся наш код, наши погодные иконки, дополнения интерфейса и скрипты обработки ресурсов.

## Первая установка — нужен root

Нужны доступный `su 0` и перемонтируемый `/system`. В командах `--user current` обозначает текущего пользователя Android; его ID можно узнать через `adb shell am get-current-user`.

```sh
adb install --no-incremental -r --user current widget/build/outputs/apk/release/widget-release.apk
adb push overlay/build/weather-overlay.apk /data/local/tmp/weather-overlay.apk
adb push tools/stage-overlay.sh /data/local/tmp/stage-overlay.sh
adb shell su 0 sh /data/local/tmp/stage-overlay.sh
```

Скрипт размещает overlay в `/system/app/WeatherWidgetOverlay/`, выставляет права и SELinux-контекст и возвращает `/system` в режим чтения.

Для первого обнаружения системного пакета нужен перезапуск Android framework. Команда ниже прервёт интерфейс и ADB/Wi-Fi; выполняйте её только на стоящей машине, когда готовы к этому:

```sh
adb shell su 0 setprop ctl.restart zygote
```

После перезапуска заново подключитесь по ADB. IP-адрес Wi-Fi машины может измениться: в этом случае обновите `ANDROID_SERIAL` командой `export ANDROID_SERIAL=DEVICE_IP:5555`, указав новый адрес. Затем включите overlay и перезапустите лаунчер, чтобы он перечитал список виджетов:

```sh
adb shell pm install-existing --user current me.elizarov.weather.overlay
adb shell cmd overlay enable --user current me.elizarov.weather.overlay
adb shell 'su 0 kill -TERM $(pidof com.flyme.auto.launcher)'
```

## Разрешения и использование

Для автоматической локации выдайте разрешения и включите геолокацию Android:

```sh
adb shell pm grant --user current me.elizarov.weather android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant --user current me.elizarov.weather android.permission.ACCESS_FINE_LOCATION
adb shell pm grant --user current me.elizarov.weather android.permission.ACCESS_BACKGROUND_LOCATION
```

Выберите город в настройках виджета. При включённой автолокации он используется как резервный: виджет определяет место по последней известной позиции Android возрастом до двух часов, а если её нет — использует выбранный город. При выключенной автолокации выбранный город используется всегда.

Нажатие на прогноз обновляет данные. Принудительное обновление через ADB:

```sh
adb shell am broadcast --user current -a me.elizarov.weather.REFRESH_WEATHER -n me.elizarov.weather/.WeatherWidgetProvider
```

## Интернет-endpoints

Во время работы приложение обращается к Гидрометцентру по HTTPS:

| Endpoint | Зачем и когда |
| --- | --- |
| `GET https://meteoinfo.ru/forecasts` | Начальная страница каталога городов — при открытии настроек, если кэша нет или он старше суток. |
| `GET https://meteoinfo.ru/forecasts/{path}` | Страницы стран/регионов/городов для обновления каталога; поиск ID станции выбранного города (результат кэшируется на сутки); дневные и ночные условия для иконок при обновлении прогноза. |
| `POST https://meteoinfo.ru/hmc-output/forecast/tab_0.php` | Подбор станции по округлённым координатам при автолокации: первое успешное определение, затем при перемещении минимум на 10 км. Неудачные попытки могут повторяться. |
| `GET https://meteoinfo.ru/rss/forecasts/index.php?s={station}` | Температуры и даты прогноза по ID станции — примерно раз в час, при смене станции/настроек или ручном обновлении. При ошибках повтор — через 10 минут. |

## Использование координат

Автолокация по умолчанию выключена. При её включении и наличии разрешений приложение берёт последнюю известную позицию Android возрастом до двух часов, без отдельного запуска GPS. Перед отправкой в Гидрометцентр широта и долгота округляются до узла сетки с шагом примерно **5 км**; исходные точные координаты в интернет не отправляются. Координаты передаются только в POST-запросе подбора станции из таблицы выше. В ручном режиме используются только выбранный город и ID станции.

Позиция проверяется локально примерно раз в 15 минут. После успешного выбора станции повторный запрос подбора станции выполняется только при удалении минимум на **10 км** от точки этого выбора; повтор по истечении времени отсутствует. Для точного расчёта расстояния в закрытых данных приложения сохраняются ID станции и одна последняя точная позиция — без истории перемещений. Старый кэш без округления заменяется после первого успешного определения по новой схеме. Обновление самого прогноза не требует повторной отправки координат. Если позиция или подбор станции недоступны, используется выбранный резервный город.

## Обновление и откат

После сборки новой версии, когда overlay уже зарегистрирован и включён:

```sh
./tools/update.sh "$ANDROID_SERIAL"
```

Обновляется только приложение, Android и лаунчер не перезапускаются. Overlay при обычных обновлениях менять не требуется.

Отключение замены: `./tools/rollback.sh "$ANDROID_SERIAL"`. Штатный виджет вернётся при следующем перезапуске лаунчера. Системный overlay можно оставить выключенным.

## Локальная проверка

Проверка скриптов на синтетических данных без подключения к машине:

```sh
python3 -m unittest discover -s tools/tests -v
```

Укажите серийный номер запущенного эмулятора в переменной `EMULATOR_SERIAL`:

```sh
./tools/test-local.sh "$EMULATOR_SERIAL"
```

По умолчанию тестируется release APK: регистрация AppWidget, RemoteViews, геометрия, часовые пояса, прогноз и кэш. Скрипт принимает только эмулятор.

Для разработки и предпросмотра соберите debug-вариант:

```sh
./gradlew -PtestBuildType=debug :widget:assembleDebug :widget:assembleDebugAndroidTest
./tools/test-local.sh "$EMULATOR_SERIAL" debug
```

В debug доступны `PreviewActivity`, галерея иконок и `OverlayProbeReceiver` для диагностики ресурсов лаунчера и состояния погоды. Эти классы и локальные шрифты предпросмотра не включаются в release. Release содержит рабочий AppWidget и настройки.

### Шрифты для эмулятора

Чтобы предпросмотр использовал шрифт цифр с машины, отдельно выполните:

```sh
python3 tools/pull-assets.py --fonts-only
```

Скрипт ищет конфигурацию и файлы шрифтов в `system`, `product`, `system_ext` и `vendor` и сохраняет доступные шрифты в `local-assets/fonts/`. Если конфигурация недоступна, проверяются ожидаемые пути к обычному начертанию `LynkcoDisplay`. Оно также копируется в `local-assets/res/font/lynkco_display.otf` для debug-сборки; отсутствие дополнительных начертаний не мешает скачиванию. Пути сохраняются в локальном manifest. Без этих шрифтов предпросмотр использует стандартный шрифт Android; на машине используются системные шрифты.

## Лицензия

[Apache License 2.0](LICENSE).
