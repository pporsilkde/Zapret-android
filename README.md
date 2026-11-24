# Zapret Android

Android-клиент для Discord, YouTube и Telegram. На главном экране выберите приложения. Кнопка «Подключить» запускает локальный VPN ByeDPI без root. Настройки ByeDPI доступны через меню ⋮.

## Профили Flowseal

Кнопка **Flowseal · Root** открывает список из 22 профилей. Выберите профиль и нажмите «Подключить». Приложение запросит root, запустит nfqws и установит правила для исходящего трафика выбранных приложений. Остановка доступна на этом экране и в уведомлении. Для смены движка сначала остановите текущий сеанс.

Режим требует Android с root, работающими iptables, NFQUEUE и доступом к очереди netfilter. При включённом IPv6 требуются также ip6tables. Используется стандартный диапазон портов каждого профиля с выключенным игровым фильтром, как при первом запуске Flowseal. Выбор Telegram направляет его пакеты на обработку в пределах этих портов. Совпадение с фильтрами профиля, доступность видео и голосовой связи зависят от протокола и сети.

## Сборка

Клонируйте репозиторий с подмодулями или выполните `git submodule update --init --recursive`. Нужны JDK 17, SDK 36, NDK 27.0.12077973 и CMake 3.22.1. Команда `./gradlew :app:assembleDebug` создаёт APK в `app/build/outputs/apk/debug/`. GitHub Actions сохраняет универсальный debug APK.

## Исходники и воспроизводимость

- Android-клиент основан на [ByeByeDPI](https://github.com/romanvht/ByeByeDPI/tree/ee87264e3bab779d3c23e41e11c45d5f8189faec).
- Профили и списки взяты из [Flowseal](https://github.com/Flowseal/zapret-discord-youtube/tree/cf018db758623ed1813b9d93a6d7f3bdf01cc2b4). Каталог генерируется командой `python3 tools/import_profiles.py <каталог-Flowseal> app/src/main/assets/flowseal`. Обработчик сохраняет порядок правил, аргументов и повторных параметров. Фильтры портов winws используются при настройке iptables.
- Исполняемые файлы в `app/src/rootJni` взяты из [zapret v72.13](https://github.com/bol-van/zapret/releases/tag/v72.13), архив `zapret-v72.13.tar.gz`, SHA-256 `25c74e6c5f48963fa244c2955e76694a07c39447245a0457e2efdc74b3317e68`. Файлы `binaries/android-*/nfqws` упакованы как нативные библиотеки для извлечения установщиком Android.
