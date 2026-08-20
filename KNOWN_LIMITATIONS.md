# Known Limitations

- Audio file codec availability follows the Android device's platform codec support because decoding uses MediaExtractor/MediaCodec.
- Bluetooth/headset behavior, audio focus, foreground/background operation and true underrun behavior must be verified on real Android hardware.
- No Android SDK/emulator is present in the current build environment, so final device-level verification is marked NOT VERIFIED.
