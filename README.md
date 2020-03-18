# ![](app/src/quarantine/res/mipmap-xxhdpi/ic_launcher.png) eQuarantine - Android

![Android 5.0](https://img.shields.io/static/v1?message=5.0&color=green&logo=android&label=Android) ![Google Play Services 13.0](https://img.shields.io/static/v1?message=13.0&color=green&logo=google-play&label=Google%20Play%20Services)

*For a Slovak version of the README, see [README_SK.md](README_SK.md).*

This app serves as a tool for monitoring of adherence to home isolation orders given by a public health authority.
It gives the public health authority a tool to perform risk assesment of users in home isolation, to detect
their violations of home isolation and perform better targeted personal presence checks, while preserving
user's privacy to a high degree. It is not meant to keep users from leaving their home isolation, as this is not a task that is solvable by an app.

The app is currently deployed in Slovakia, under the Slovak Public Health Authority ([Úrad verejného zdravotníctva](http://www.uvzsr.sk/)),
operated by the National Health Information Centre ([Národné centrum zdravotníckych informácii](http://www.nczisk.sk/Pages/default.aspx)).
This version of the app can be downloaded from the [Google Play store](https://play.google.com/store/apps/details?id=sk.nczi.ekarantena)
and [Apple app store](https://apps.apple.com/sk/app/ekarantena-slovensko/id1513127897). More information about this
deployment of the app can be found on [korona.gov.sk/ekarantena/](https://korona.gov.sk/en/smart-quarantine-self-isolation-as-an-alternative-to-institutional-quarantine/). The app was developed by various volunteers in cooperation with the National Health Information Centre (NCZI).

## Privacy

The app was developed with privacy in mind. It uses the minimal amount of user information that is possible for it
to work. It also processes this information only locally, while sending only the information about isolation
violations to the server.

The app uses sensitive user data, namely a facial biometric to verify the user's presence and the position of the
device to verify the user's position. This data is only used locally, it is evaluated against the stored coordinates
of the user's home isolation and the stored biometric template of the user's face. The app does not store the
user's position.

## Functionality

The app has several ways to monitor the users adherence to home isolation, a passive one (Area-exit notification) and an active one (Presence checks).

### Area-exit notification
The app periodically checks the location of the user's device and compares it to the coordinates of the
user's selected home isolation location. If the device moved farther than a set threshold (accounting for
accuracy of the location) an alert is sent to the server, along with the significance of the isolation violation
(the distance from the home isolation location) but not the device's position. If network connectivity is not
available during this time, the violations are logged locally and sent once it becomes available.

Along with this monitoring, the app monitors the status of services on the device that are required for
the app to work properly (enabled location services, allowed camera access for presence checks, allowed
location access and background location access, disabled location mocking and no forbidden apps installed)
and sends periodic heartbeat messages containing this information to the server.

### Presence check
The app server challenges the user to perform a presence check using the app several times a day,
at random time intervals. The user is alerted to the presence check by a text message from the public health authority.
A presence check consists of two verifications, verifying the device's presence at the location of
the user's home isolation and verifying the user's presence near the device. The location of the device
is verified using location services (Geofencing API) which internally use GPS/Galileo/GLONASS (GNSS)
along with Cell ID and WiFi BSSID identifiers to establish the location. User presence is verified
by using a facial biometric scan with an active liveness check (eye movement detection with a given
random challenge) and comparison of the resulting facial template with the stored one.

## Security

*Full security design and analysis documents will be available shortly, the following is a short summary.*

Application security for such a privacy sensitive application is paramount. We focus on two main
security goals. **To secure the user's data such that the application/system will not leak it.**
**To secure the app against malicious user's trying to circumvent it to break home isolation undetected.**

To achieve the first goal, we minimize the amount of data leaving the user's device significantly.
Only home isolation violations are transmitted, along with the heartbeats and presence check responses,
which however do not contain sensitive user's data and are only identified by a pseudonymous user
identifier and device identifiers. We also use TLS with certificate pinning to assure that the app
is communicating only with a legitimate backend server.

To achieve the second goal, we introduce a large number of countermeasures against several attacks
in the threat model (we assume existence of a skilled technical attacker, potentially motivated by
monetary gains from providing a solution that allows users to break home isolation undetected). Several
attacks are **in the scope** of our threat model:

 - User leaving the home isolation with their device
 - GPS spoofing with a fake GPS app (Location Mock on Android)
 - Camera spoofing with a fake Camera app
 - Application tampering/hooking (i.e. removing the user presence and location checks during a presence check)
 - Application cloning (i.e. compiling a custom version of the application from these sources, that does
   not report violations)
 - User mis-enrollment (i.e. enrolling a face of a different user than the one that is supposed to home isolate,
   possibly even enrollment of one user's face in many users' devices)
 - Biometric spoofing (i.e. playing a video of a user's face during a presence check)
 - Change of enrollment data (i.e. modifying the location of the home isolation or facial biometric template
   which are stored locally)
 - Replay attacks (i.e. replaying the OK response to a presence check to the server, perhaps as MiTM)

The following attacks are specifically **out of scope** of our threat model:

 - User leaving the home isolation without the device
 - Hardware GPS spoofing (i.e. using an RF transmitter to spoof GPS signal)

Our set of countermeasures against attacks in the threat model includes:

 - Detecting when the device leaves the home isolation location and reporting a violation.
 - Detecting location mocking and installation of apps that allow location mocking.
 - Detecting installation of apps that allow camera spoofing.
 - Performing SafetyNet attestation (that is validated server-side) during important application
   tasks, to detect rooting/hooking of the application and to verify app legitimacy.
 - Sending a PUSH notification to the app, using Firebase Cloud Messaging, containing a unique secret
   that is required to register the app with the backend, to verify app legitimacy.
 - Sending a HOTP secret to the app, only upon successful registration and verification of app
   legitimacy, that is used during border-crossing/home isolation entry, to perform a challenge-response
   HOTP verification of the knowledge of this secret, between a trusted authority (police officer, public
   health authority personnel) and the app. This ensures that app legitimacy is validated upon entry
   to home isolation.
 - Facial biometric verification during border-crossing/home isolation entry that is performed
   under supervision of a trusted authority, to verify that the correct user is enrolled in the app.
 - Challenge-based liveness check during facial biometric verification.
 - Usage of a hardware-backed ECDSA P-256 signing key, to sign all application responses to the server.
 - TLS certificate pinning.
 - Usage of random short-lived server-provided nonces that are signed in the application response to a presence check,
   to stop replay attacks.

## Country-specific customisation steps
* Rename app/app.properties.example to app/app.properties and fill in your local-specific values
* Register your app in Google Firebase console and copy google-services.json file to app folder
* Create a flavor in app/build.gradle for your country
* Copy CountryDefaults.java from the global flavour into yours and implement your specifics
