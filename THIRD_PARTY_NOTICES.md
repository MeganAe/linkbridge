# Third-party notices

## HevSocks5Tunnel

LinkBridge bundles `hev-socks5-tunnel.aar` version 2.18.0 from:

https://github.com/heiher/hev-socks5-tunnel

License: MIT. The license text is available in the upstream repository and is included in the release source distribution.

HevSocks5Tunnel provides the userspace TCP/UDP/IP stack used to turn Android's `VpnService` TUN interface into a SOCKS5 client. LinkBridge supplies the local SOCKS5 gateway over Wi-Fi Direct.

## Inter

LinkBridge embeds the Inter typeface, version 4.1, as static TrueType files:

- Android: `app/src/main/res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`,
  `inter_bold.ttf`
- Windows: `desktop/src/main/resources/fonts/inter-regular.ttf`, `inter-medium.ttf`,
  `inter-semibold.ttf`, `inter-bold.ttf`

Source: <https://github.com/rsms/inter>

Copyright (c) 2016 The Inter Project Authors.

License: SIL Open Font License, Version 1.1. The full text is included in
[`licenses/INTER_OFL.txt`](licenses/INTER_OFL.txt) and ships with the source distribution.

The files are used unmodified. No font is downloaded at runtime, which keeps the product usable
on a slow or absent connection.

## androidx.core:core-splashscreen

LinkBridge uses `androidx.core:core-splashscreen` 1.2.0 as a runtime dependency of the Android
module to show the launch screen on Android 8 through Android 15.

Source: <https://developer.android.com/jetpack/androidx/releases/core>

License: Apache License 2.0.
