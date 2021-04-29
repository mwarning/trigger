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

## Import SSH Key As QR-Code

Use e.g. `qrencode -t ansiutf8 < .ssh/id_ed25519` to show an SSH private key as QR-Code. Scan with trigger and add the server address, user, command etc..

## Nuki Smartlock Pairing

Steps to pair Trigger with the Nuki Smartlock:

1. Nuki: Press the door knob button for 3 seconds until the ring lights up (pairing mode).
2. Phone: Enable Bluetooth.
3. Phone: Pair phone and Nuki Smartlock.
4. Phone: Start Trigger, add a new door entry with door type "Nuki SmartLock".
5. Phone: Enter the name of the paired Nuki Smartlock into the "Lock Name/MAC" field (something like "Nuki_1DAB5E34").
6. Phone: Enter some user name (not very important) and save the door setup.
7. Nuki: If the Nuki smartlock is not in pairing mode anymore, just press the the button again.
8. Phone: Press the open door button in Trigger. This will cause Trigger to register with the Nuki Smartlock.

Now you should be able to send open/close commands.

(tested with Android 10 and Nuki SmartLock 2.0)

## Build Trigger from Sources

On Linux based systems:

```
./gradlew assembleRelease
```
