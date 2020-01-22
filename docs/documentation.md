# Documentation

## Compatible Locks

Trigger makes rather generic requests and needs to be configured depending on the door system. No off the shelf door systems have been tested. So no advice can be given here.

## Door Status

The door status is determined based on the text returned of the HTTPS query or SSH command. Recognized are the keywords `LOCKED` (door closed) and `UNLOCKED` (door open) that can occure anywhere in the response, even in HTML tags. If neither keyword is found, the door status is set to unknown. The complete return message is always displayed in the App for a short time (with HTML elements stripped).

## Auto-Select Door By SSID

The door setup can be selected depending on what WiFi network the device is connected to. This can help to avoid to switch the door setup by hand. Even multiple SSIDs can be set as a comma separated list.

## SSH Key Registration

SSH Public Keys can be send to an IP address and port (e.g. `192.168.1.1:3333`) to be registered. There is a field and button in the SSH Key Managment for that. A simple example to collect keys using netcat: `nc -k -l 3333 -c 'cat >> ssh_keys.txt; echo "Your key was received!";'`

## Import Link As QR-Code

Instead QR-Code imports from another Trigger app, you can import simple links like `https://example.com/open?pass=secret` as QR-Code to create a simple HTTPS based door setup. Links starting with `ssl://` or `tcp://` will be used for MQTT and `ssh://` for SSH based door setups.

## Build Trigger from Sources

On Linux based systems:

```
./gradlew assembleRelease
```
