Trigger
=======

Trigger is an Android App to unlock/lock doors and request the status.

Features:
 - HTTPS and SSH calls
 - multiple door profiles
 - auto select profiles by connected WiFi
 - certificate/key file support

![image](screenshot_states.png)

(door status open/closed/unknown/disabled)

![image](screenshot_settings.png)

(https setup, ssh setup, ssh key pair management)

Any help, bugfixes, new features, translations are much welcome.

## Download

[<img src="fdroid.png" alt="Get it on F-Droid" height="90">](https://f-droid.org/packages/com.example.trigger/)

Alternatively, you can download packages (apk) in the [release](https://github.com/mwarning/trigger/releases) section.

The minimum supported Android version is 5.0.

## Usage

The status of the door is determined if the https query or ssh command returns the strings `LOCKED` (door closed), `UNLOCKED` (door open) or some other output (unknown door state). The return message is displayed in the App for a short period.

## Status

- HTTPS/SSH calls work.
- Sending the ssh public key as a registration mechanism is missing.
- The ssh key pair and certificate cannot be explicitly removed right now (but replaced).

## Build from Sources

On Linux based systems:

```
./gradlew assembleRelease
```

## Related Projects

* [D00r](https://github.com/h42i/d00r-app)
* [labadoor](https://github.com/ToLABaki/labadoor)
* [Sphincter](https://github.com/openlab-aux/sphincter)/[Sphincter-Remote](https://github.com/openlab-aux/Sphincter-Remote)
* [Krautschlüssel](https://gitlab.com/fiveop/krautschluessel)

Icons: [Googles Material Design](https://material.io/tools/icons/)

## License

This work is licenced under the GNU General Public License version 2 (GPLv2).
