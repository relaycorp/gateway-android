<img src="./relaynet-logo.png" align="right"/>

# Relaynet Gateway for Android

This repository contains the source code for the [Relaynet Gateway for Android](https://play.google.com/store/apps/details?id=tech.relaycorp.gateway), which is all Relaynet users need on their Android devices. To learn more about _using_ Relaynet, visit [relaynet.network](https://relaynet.network/users).

This document is aimed at advanced users and (prospective) contributors. We aim to make the app as simple and intuitive as possible, and we're therefore not planning on publishing end-user documentation at this point.

## Overview

As a private gateway, the primary responsibility of this app is to enable the private endpoints on the device to communicate with endpoints on other devices, whether the Internet is available or not. It does so by collaborating with a public gateway such as Relaycorp's [Relaynet-Internet Gateway](https://docs.relaycorp.tech/relaynet-internet-gateway/).

The minimum Android OS version supported is Android 5 (Lollipop, API 21), but Android 6+ is recommended for security reasons.

## Architecture


### Communication with local endpoints

### Communication with public gateway

### Communication with couriers

### Communication with external services

In addition to communicating with its public gateway, this app communicates with the following:

- `https://dns.google/dns-query` as the DNS-over-HTTPS resolver, which [we plan to replace with Cloudflare](https://github.com/relaycorp/relaynet-gateway-android/issues/249).
- `https://google.com`. When the public gateway can't be reached, this app will make periodic GET requests to Google to check if the device is connected to the Internet and thus provide the user with a more helpful message about the reason why things aren't working. We chose `google.com` because of its likelihood to be available and uncensored.
- The DHCP server on the local network, when the device is connected to a WiFi network but disconnected from the Internet. [This is the least unreliable way to get the default internet gateway on Android](https://stackoverflow.com/questions/61615270/how-to-get-the-ip-address-of-the-default-gateway-reliably-on-android-5), which we need to discover the IP address of the courier. Consequently, we assume that the courier's IP address in its hotspot network is the same as the DHCP server's, and we initiate a [CogRPC](https://specs.relaynet.network/RS-008) connection with that server on port `21473` in order to ensure it is indeed a courier.

## Security considerations

We use the [Android Keystore system](https://developer.android.com/training/articles/keystore) to protect sensitive cryptographic material, such as long-term and ephemeral keys. Unfortunately, [Android 5 doesn't actually encrypt anything at rest](https://issuetracker.google.com/issues/132325342#comment29), and [we plan to address this in the near future](https://github.com/relaycorp/relaynet-gateway-android/issues/247).

For a more general overview of the security considerations in Relaynet, please refer to [RS-019](https://specs.relaynet.network/RS-019). 

## Development

The project should build and run out-of-the-box with [Android Studio](https://developer.android.com/studio/) 4+.

## Contributing

We love contributions! If you haven't contributed to a Relaycorp project before, please take a minute to [read our guidelines](https://github.com/relaycorp/.github/blob/master/CONTRIBUTING.md) first.
