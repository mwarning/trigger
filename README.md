Trigger
=======

Trigger is an Android App to unlock/lock doors and request the status.

Features:
 - HTTPS, SSH, Bluetooth and MQTT support
 - multiple door profiles
 - auto select profiles by SSID
 - certificate/key file support
 - QR code import/export
 - JSON file import/export
 - custom status images

![image](screenshot_states.png)

(door status open/closed/unknown/no-wifi)

![image](screenshot_settings.png)

(ssh setup and key pair management, https setup and certificate management)

Any help, bugfixes, new features, translations are much welcome.

## Download

[<img src="fdroid.png" alt="Get it on F-Droid" height="90">](https://f-droid.org/packages/com.example.trigger/)
[<img src="apk.png" alt="Get it on GitHub" height="90">](https://github.com/mwarning/trigger/releases)

The minimum supported Android version is 5.0.

## Door Status

The door status is determined based on the text returned of the HTTPS query or SSH command. Recognized are the keywords `LOCKED` (door closed) and `UNLOCKED` (door open) that can occure anywhere in the response, even in tags. If neither keyword is found, the door status is set to unknown. The complete return message is always displayed in the App for a short time (with HTML elements stripped).

## Auto-Select By SSID

The door setup can be selected depending on what WiFi network the device is connected to. This can help to avoid to switch the door setup by hand. Even multiple SSIDs can be set as a comma separated list.

## Build from Sources

On Linux based systems:

```
./gradlew assembleRelease
```

## Similar/Related Projects

* [Sphincter-Remote](https://github.com/openlab-aux/Sphincter-Remote) / [Sphincter](https://github.com/openlab-aux/sphincter)
* [D00r-app](https://github.com/h42i/d00r-app) / [D00r-key-server](https://github.com/h42i/d00r-key-server)
* [labadoor](https://github.com/ToLABaki/labadoor) / [DoorLock](https://wiki.tolabaki.gr/w/DoorLock_v3)
* [Krautschlüssel](https://gitlab.com/fiveop/krautschluessel)
* [MetalabDoorWidget](https://github.com/zoff99/MetalabDoorWidget)
* [HACKS](https://github.com/ktt-ol/hacs)
* [Stratum0Widget](https://github.com/Valodim/Stratum0Widget)

## License

This work is licenced under the GNU General Public License version 2 or later (GPLv2).

Icons: [Googles Material Design](https://material.io/tools/icons/)
