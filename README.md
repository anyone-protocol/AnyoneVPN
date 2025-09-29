<div align="center">

<img width="" src="./docs/assets/ic_launcher.svg" width="128" height="128" alt="AnyoneBot" align="center"/>

# AnyoneBot

### Android Anon Robot

AnonBot is a freely licensed open-source application developed for the
Android platform. It acts as a front-end for the Tor binary application,
while also providing a secure HTTP Proxy for connecting web browsers and other
HTTP client applications into the Tor SOCKS interface.

***********************************************
<img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/device-2024-01.png width="24%"> <img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/device-2024-02.png width="24%">
<img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/device-2024-03.png width="24%"> <img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/device-2024-04.png width="24%">

***********************************************
Orbot is a crucial component of the Guardian Project, an initiative  that leads an effort
to develop a secure and anonymous smartphone. This platform is designed for use by human rights
activists, journalists and others around the world. Learn more: https://guardianproject.info/

***********************************************
Tor protects your privacy on the internet by hiding the connection
between your Internet address and the services you use. We believe that Tor
is reasonably secure, but please ensure you read the usage instructions and
learn to configure it properly. Learn more: https://torproject.org/

***********************************************

<div align="center">
  <table>
    <tr>
      <td><a href="https://github.com/guardianproject/orbot/releases/latest">Download the Latest Orbot Release</a></td>
    </tr>
    <tr>
      <td><a href="https://support.torproject.org/faq/">Tor FAQ (Frequently Asked Questions)</a></td>
    </tr>
    <tr>
      <td><a href="https://hosted.weblate.org/engage/guardianproject/">Please Contribute Your Translations</a></td>
    </tr>
  </table>
</div>

***********************************************

### Build Instructions

```sh
git clone https://github.com/anyone-protocol/AnyoneBot.git
cd AnyoneBot
git submodule update --init --recursive
cd anyonebotservice/src/main
ndk-build # Make sure your Android NDK folder is on the PATH.
mv libs jniLibs
 ```


**Copyright &#169; 2009-2023, Nathan Freitas, The Guardian Project**
