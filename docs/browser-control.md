# Samsung Browser Control

Open the SetStone tab in Uh-Ah. Browser Control starts ON and has its own
saved switch. If the screen says Accessibility permission is needed, select
**Open Accessibility settings** and enable Uh-Ah yourself. Android allows
you to switch the service off from the same settings.

When connected, the service reacts to window and search-field changes and
requests Android Home for a confirmed match. It recognizes Samsung Browser
and Beta by Android package name, including launches from a link. It
does not force-stop either browser. Android can show a screen before it
delivers the accessibility event.

For Play Store and Galaxy Store, the matcher needs an exact target query
in a recognized search field or app identity in a compact app card or
product header. It uses the package ID where exposed or pairs a known
title with Samsung's publisher and an app action or clickable card.
Layouts that expose neither are skipped. The test emulator reached Play
Store sign-in. Signed-in Play Store and Galaxy Store layouts still need
device validation.

In Android Settings, the matcher uses target search entries when exposed,
App info headers with package evidence or an unambiguous installed
package label, and explicitly selected browser rows on supported default
browser layouts. Generic app lists and disabled-app filters remain usable.
One UI, alternate languages, and changed Settings layouts can expose
different information. A disposable app under the stable target package
exercised the launch and Compose App info paths on the API 37 emulator.

## Optional Android package disabling

If your phone offers it, open **Settings → Apps → Samsung Browser →
Disable** before turning Browser Control on. Do the same for Beta if
installed. Some devices do not offer Disable. Uh-Ah reports the enabled
state Android exposes to it. Browser Control itself uses monitoring and
does not hide, suspend, uninstall, or change either package. Package
hiding and suspension require qualifying Android management privileges
and a distinct device-management setup.

[Android DevicePolicyManager reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)

## Device-level validation still needed

- On a Samsung phone, open both browser variants directly and through a
  link with Browser Control ON, then repeat with it OFF.
- With a signed-in Play Store and Galaxy Store, try exact target searches,
  unrelated searches, result taps, and direct product links. Check the
  screens that expose Install, Enable, Open, or Update.
- In One UI Settings, check App info for the preinstalled browser, the
  disabled-app list and re-enable page, Settings search, and the default
  browser picker. Confirm that unrelated app pages stay accessible.
