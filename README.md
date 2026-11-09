# min

A tiny, framework-only Android text launcher. It has no external UI dependencies: home and search are plain Android views, state is local, and `UiTokens.java` is the single visual/motion token file.

## Build

Use JDK 17 and an Android SDK with API 35 installed:

```sh
./gradlew --no-daemon assembleDebug
```

Install the resulting `app/build/outputs/apk/debug/app-debug.apk`, choose **min** as the Android Home app if prompted, and grant Contacts only if contact results are wanted.

## ADB control

The exported receiver action is `dev.min.launcher.COMMAND`; it returns its synchronous text response as broadcast result data. Wrap a command containing spaces as one `cmd` extra:

```sh
adb shell am broadcast -a dev.min.launcher.COMMAND --es cmd "status"
adb shell am broadcast -a dev.min.launcher.COMMAND --es cmd "search chrome"
adb shell am broadcast -a dev.min.launcher.COMMAND --es cmd "set_setting anim_speed snappy"
```

Run `help` through the same receiver for the complete command list. For shell layers that consume quotes, quote the entire `cmd` value again as documented by the tool invoking ADB.
