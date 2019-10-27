# Documentation

## Door Status

The door status is determined based on the text returned of the HTTPS query or SSH command. Recognized are the keywords `LOCKED` (door closed) and `UNLOCKED` (door open) that can occure anywhere in the response, even in tags. If neither keyword is found, the door status is set to unknown. The complete return message is always displayed in the App for a short time (with HTML elements stripped).

## Auto-Select Door By SSID

The door setup can be selected depending on what WiFi network the device is connected to. This can help to avoid to switch the door setup by hand. Even multiple SSIDs can be set as a comma separated list.

## SSH Key Registration

SSH Public Keys can be send to an IP address (like `192.168.1.1:3333`) to be registered. A simple example to collect keys using netcat: `nc -k -l 3333 -c 'cat >> ssh_keys.txt; echo "Your key was received";'`

## Build Trigger from Sources

On Linux based systems:

```
./gradlew assembleRelease
```
