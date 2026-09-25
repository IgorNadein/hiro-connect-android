# HI-RO Connect

Android-приложение рабочего места оператора для HI-RO Phone Gateway. Телефон менеджера регистрируется на локальном шлюзе по SIP и получает входящие вызовы через штатный Android Telecom UI, в том числе при заблокированном экране.

## Экосистема

| Компонент | Репозиторий |
| --- | --- |
| Самохостинговый сервер и телефонный шлюз | [HI-RO Server](https://github.com/IgorNadein/hiro-server) |
| Android SMS-шлюз | [HI-RO Messages](https://github.com/IgorNadein/hiro-messages-android) |
| Спецификация авторизации и федерации | [HI-RO Protocol](https://github.com/IgorNadein/hiro-protocol) |

Архитектура не требует центрального аккаунта HI-RO: пользователь выбирает сервер своей организации, а сервер самостоятельно определяет способ авторизации. Текущая сборка использует SIP-аккаунт; переход к серверному входу описан в [плане подключения](docs/SERVER_CONNECTION.md).

## Возможности

- входящие и исходящие SIP-вызовы;
- системный экран вызова Android и фоновый foreground service;
- контакты, история звонков и SIP-сообщения;
- работа с локальным шлюзом по TCP без внешнего push-провайдера;
- голосовой аудиорежим Android (`VOICE_COMMUNICATION`);
- увеличенные OpenSL ES очереди для более устойчивого звука;
- интерфейс оператора на русском и английском языках.

## Архитектура

```text
Телефон-шлюз + SIM → HI-RO Phone Gateway / Asterisk → SIP/RTP → HI-RO Connect
```

HI-RO Connect — клиент менеджера. SIM-карта и Bluetooth HFP находятся на стороне шлюза, а приложение менеджера получает уже маршрутизированный SIP-вызов.

## Сборка

Требования:

- Android Studio или JDK 21;
- Android SDK 37;
- Android NDK `27.1.12297006`;
- CMake 3.22.1;
- Linux-пакеты `wget cmake make libtool m4 automake pkg-config unzip`.

Подготовка нативных библиотек:

```bash
git submodule update --init
make -C libbaresip-android download-sources
./scripts/apply-audio-patch.sh
make -C libbaresip-android libbaresip ANDROID_TARGET_ARCH=arm64-v8a
```

Скопируйте собранные заголовки и библиотеки из `libbaresip-android/distribution` в корневой каталог `distribution`, затем соберите приложение:

```bash
./gradlew :app:assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Настройка

Репозиторий намеренно не содержит адресов, логинов или паролей рабочего шлюза. SIP-аккаунт добавляется в приложении через меню **Аккаунты**. Для локальной установки обычно нужны адрес шлюза, внутренний номер менеджера, пароль и транспорт TCP.

## Происхождение и лицензии

Проект основан на [baresip-studio](https://github.com/juha-h/baresip-studio) и использует [baresip](https://github.com/baresip/baresip) через [libbaresip-android](https://github.com/juha-h/libbaresip-android). Исходный код распространяется по BSD-3-Clause; исходное уведомление об авторских правах сохранено в `LICENSE`, подробности — в `NOTICE.md`.
