# SafeLook VPN Security

Open the SetStone tab in Uh-Ah and enable VPN Security. If the page offers
**Open Accessibility settings**, use it to enable Uh-Ah's accessibility
service in Android. You can disable the service from those same settings.

Uh-Ah targets the Android Settings disconnect confirmation for SafeLook
(`com.pro.SafeLook`). It checks the installed VPN service and the system
dialog's title, message, and buttons. When those checks pass, it attempts
to click Cancel and then send the device to Home. Other dialog layouts,
ambiguous VPN labels, and devices with multiple visible profiles are
skipped. The feature is best effort. A user action or a system UI change
can race the accessibility handler. Switching VPN Security off stops
this handling.

## Android Always-on VPN and connection blocking

The local SafeLook project declares an Android VPN service, targets API
36, and does not opt out of Android's always-on capability. Android
determines whether the options are available for the installed build
and device. The SafeLook 1.0 build on the API 37 test emulator showed
both options. To check on your device, open **Settings → Network & internet → VPN** and
tap the settings icon beside SafeLook. If offered, enable **Always-on
VPN**, then **Block connections without VPN** if you want Android to
block traffic outside the VPN. Menu names vary by device.

SafeLook's local VPN is designed to filter DNS. Android's connection
blocking can affect connectivity for traffic outside the VPN's routes.
Check connectivity on the device after enabling it. Uh-Ah leaves both
Android settings under user control.

[Android VPN documentation](https://developer.android.com/develop/connectivity/vpn)
