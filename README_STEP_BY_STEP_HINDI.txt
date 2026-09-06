AES SCHOOL BELL - Android App + OTA Firmware Update

1. Android Studio me AESSchoolBellApp folder open karein.
2. Phone connect karke Run karein, ya Build > Build APK(s) se APK banayein.
3. App me S-01/S-02/S-03 select karein.
4. ESP32 Wi-Fi se phone connect karein (automatic request bhi app karega).
5. CONNECT TO SCHOOL BELL dabayein. Original ESP32 dashboard WebView me khulega.
6. Firmware update ke liye launcher par FIRMWARE UPDATE (OTA) dabayein.
7. Naya ESP32 .bin select karein.
8. UPDATE confirm karein. App pehle ESP32 web login karega, phir .bin ko /ota_upload par stream karega.
9. Upload complete hone par ESP32 automatically restart hoga. 10-20 seconds baad Wi-Fi reconnect karke dashboard kholen.

IMPORTANT:
- OTA ke liye .ino source nahi, compiled ESP32 .bin file chahiye.
- Naya firmware banate waqt FIRMWARE_VERSION badalna recommended hai.
- Current firmware me browser OTA route /ota_upload pehle se available hai; app usi route ka use karta hai.
- App update ki zarurat nahi hoti jab sirf ESP32 firmware/dashboard update hota hai, jab tak web routes compatible rahen.
- Android 10+ Wi-Fi connection par system approval dialog aa sakta hai; Android security ke kaaran silent Wi-Fi switching guaranteed nahi hai.
- Play Store release ke liye signed AAB, privacy policy, store screenshots/listing aur Play Console account alag se chahiye.
