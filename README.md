# SunProxy

Use VPN for proxy (redirect TCP packets, including HTTP proxy), custom DNS, and custom hosts file.

## Redirect rule syntax

I am too lazy to explain.
See `app/src/test/java/io/github/ulysseszh/sunproxy/UtilsTest.kt` for examples.

## Usage notes

For DNS features, turn off private DNS in system settings and browser settings.

Redirect rules based on hostname do not work for HTTP but works for HTTPS (sometimes).
This is because the HTTP headers are not complete in one TCP packet,
but the socket is already opened when the first packet is initiated.
For HTTPS, because the hostname can be read from the SNI in the TLS handshake,
the hostname is known before the socket is opened.

Redirect rules based on the presence of TLS is also not reliable
because it is actually based on whether there is SNI in the TLS handshake.
TLS without SNI will be falsely determined as non-TLS.

## Build

```shell
./gradlew build
```

## License

This project is licensed under GPL-3.0-or-later.

The whole project is a rewrite of [TunProxy](https://github.com/raise-isayan/TunProxy),
which is in turn a fork of tun2http (original repo deleted),
which does not have an open-source license but should be GPL-3.0-or-later
because it contains GPL-3.0-or-later licensed codes from
[NetGuard](https://github.com/M66B/NetGuard).
It also contained some codes from
[SNI Proxy](https://github.com/dlundquist/sniproxy),
which is licensed under BSD-2-Clause.

In this rewrite, I used the codes from the latest commit (bdf74ec) of NetGuard,
and I restored NetGuard's original license notice in the source codes
and marked every change I made to the codes.
