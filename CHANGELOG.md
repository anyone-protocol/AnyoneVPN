# AnyoneVPN Changelog

## 0.1.9
- Improved no-internet image.

## 0.1.8

- Fixed nointernet image.
- Updated build system.

## 0.1.7

- Exclude apps: Sort excluded at top.
- Exclude apps: Added toggle to show/hide system apps.

## 0.1.6

- Updated Gradle, AGP and dependencies.
- Reworked "Choose apps" (allowlist) to "Exclude apps" (denylist).
- Updated geoip files.
- Updated exit country list.

## 0.1.5

- Added select all / deselect all option to "Choose Apps" activity.
- Fixed bug regarding hidden services support.
- Encapsulate core VPN code in its own process for better stability.

## 0.1.4

- Always show "Choose apps" and "Change exit" buttons.
- Fixed crash on stopping.

## 0.1.3

- Updated exit node country list again.
- Fixed crash on Android Nougat.
- Updated Android Gradle Plugin.
- Fixed bottom layout in main activity.
- Fixed update of app list in main activity.

## 0.1.2

- Removed Russia from exit country list. County too censored, anyone not working there.
- Reworked app selection to be in a standard activity instead of a bottom sheet to avoid 
  colliding UI interactions of scrolling down the list and closing the bottom sheet with the swipe.

## 0.1.1

- Fixed notification button functionality.
- Updated exit country list.
- Updated geoip database.
- Updated hev-socks5-tunnel dependency.

## 0.1.0

- Initial release.