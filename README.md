Sphincter-Remote
================

Sphincter-Remote is an Android app to unlock doors via simple HTTPS calls.
A server side program that works with this app is [Sphincter](https://github.com/openlab-aux/sphincter).

![image](screenshots.png)

## Download

## Build from Sources

On Linux based systems:

```
./gradlew assembleRelease
```

## API

Sphincter-Remote allows you to set an URL (e.g. `https://example.com/door/`) and a token (e.g. `secret123`). When the open or close button is pressed, a HTTP request will be send. To open a door this might be the following:

```
https://example.com/door/?action=open&token=secret123
```

Sphincter uses `open`, `close` or `state` as an action.
As a reponse, `LOCKED` or `UNLOCKED` may be returned.

An option allows to ignore certificate errors. But beware, that's dangerous!
