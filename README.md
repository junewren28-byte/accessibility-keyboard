# SpeechBoard

SpeechBoard is a minimal Android input method for users who need a very large
speech target.

- Tap anywhere on the keyboard panel to dictate.
- Tap again to stop listening.
- Long-press anywhere to open SpeechBoard's built-in typing keyboard.
- Password and other sensitive fields open the typing keyboard automatically.
- Tap SPEAK on the typing keyboard to return to speech mode.

SpeechBoard does not request network access. Android's on-device speech
recognizer is used when it is available.

## Build

Run:

```sh
./build.sh
```

The signed APK is written to `build/SpeechBoard.apk`.

## Setup

1. Open SpeechBoard from the launcher.
2. Grant microphone access.
3. Enable SpeechBoard in Android's on-screen keyboard settings.
4. In a text field, use Android's keyboard switch button to select SpeechBoard.

Keep SpeechBoard selected as the active keyboard. Long-press its speech panel
to type without leaving SpeechBoard; tap SPEAK to return to speech mode.

## Debug logging

SpeechBoard writes lifecycle, keyboard-switching, and speech-recognition status
under the `SpeechBoard` log tag. It does not log recognized text.

From a computer connected with Android debugging enabled:

```sh
adb logcat -s SpeechBoard
```

## Launch diagnostic

The instrumentation test distinguishes installation, enablement, selection,
and actual input-view launch. Build and install both `SpeechBoard.apk` and
`SpeechBoardTest.apk`, then run in Termux:

```sh
./run-launch-test.sh
```

It exits nonzero and prints the first failed condition. Its final assertion
requires `SpeechInputMethodService.onStartInputView()` to run after the test
focuses the editor; merely having the package installed cannot pass it.
