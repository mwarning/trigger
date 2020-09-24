# Documentation

## Compatible Locks

Trigger makes rather generic requests and needs to be configured depending on the door system. No off the shelf door systems has been tested. So no advice can be given here. While the Nuki SmartLock has basic support now, it is rather expensive.

## Door Status

Trigger reads the door status from the text returned of the HTTPS query, SSH command or MQTT server. You can set a regular expression for the `Reply Pattern (locked)` and `Reply Pattern (unlocked)` settings. The default values are `LOCKED` and `UNLOCKED`. The complete return message is always displayed in the App for a short time (with possible HTML elements stripped and truncated if needed).

An example of a regular expression pattern is `state"\s*:\s*"open`. This would match a part of a typical reponse that is in JSON format.

## Auto-Select/Limit Door By SSID

The door setup can be selected depending on what WiFi network the device is connected to. This can help to avoid to switch the door setup by hand. Even multiple SSIDs can be set as a comma separated list. As of release 3.1.1, this disables the setup if the connected WiFi SSID does not match.

## SSH Key Registration

SSH Public Keys can be send to an IP address and port (e.g. `192.168.1.1:3333`) to be registered. There is a field and button in the SSH Key Managment for that. A simple example server to collect keys using netcat: `
nc -l -k -p 3333 -c 'read key; echo "$key" >> ssh_keys.txt; echo "Your key was received!"'`.

## Import Link As QR-Code

Instead QR-Code imports from another Trigger app, you can import simple links like `https://example.com/open?pass=secret` as QR-Code to create a simple HTTPS based door setup. Links starting with `ssl://` or `tcp://` will be used for MQTT and `ssh://` for SSH based door setups.

## Build Trigger from Sources

On Linux based systems:

```
./gradlew assembleRelease
```
