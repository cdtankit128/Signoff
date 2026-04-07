# SignOff - Smart Proximity-Based Auto-Lock for Windows 🔒

![Platform](https://img.shields.io/badge/Platform-Windows-blue.svg) ![Mobile](https://img.shields.io/badge/Companion_App-Android-green.svg) ![Language](https://img.shields.io/badge/Backend-Kotlin%20%7C%20Spring%20Boot-orange.svg) ![License](https://img.shields.io/badge/License-MIT-brightgreen.svg)

**SignOff** is an open-source, hybrid **proximity-based auto-lock system** for Windows. Built as a powerful alternative to Windows Dynamic Lock, it uses your Android smartphone as a digital presence beacon to automatically lock your Windows workstation the moment you step away from your desk.

*Keywords: Windows auto lock, proximity lock, Bluetooth screen lock, Auto lock Windows using Android, Dynamic Lock alternative, BLE proximity, Spring Boot WebSockets, automatic PC lock, walk away screen lock, distance based computer lock, smart lock for PC, secure workstation automatically, lock screen automatically, PC auto locker, Android beacon lock, open source Dynamic lock, Windows Bluetooth detector, PC anti-theft, workstation security automation, hands-free computer lock, detect phone away lock PC.*

## 🚀 Features

- **Hybrid Connectivity**: Seamlessly switch between two connection modes depending on your environment:
  - **Wi-Fi Mode**: Uses high-speed WebSockets over your local network. It leverages your phone's accelerometer to intelligently lock the screen when you physically walk away.
  - **BLE Beacon Mode**: For environments like mobile hotspots where Wi-Fi routing doesn't work. The phone acts as a Bluetooth Low Energy beacon, and the laptop locks based on RSSI (signal strength) proximity.
- **Zero-Config Auto-Discovery**: The Android app automatically discovers your laptop on the local network via UDP/TCP scanning—no need to manually type IP addresses.
- **Smart Grace Periods**: Built-in algorithms prevent accidental locks by waiting for a configurable grace period, checking motion thresholds, and applying lock cooldowns.
- **Real-Time Dashboard**: Includes a sleek, glassmorphic Web Dashboard served by the desktop app to monitor your phone's status, signal strength, and lock history in real-time.
- **Power Efficient**: Designed to run with minimal battery impact on your Android device through optimized heartbeat intervals.

## ❓ Why SignOff? (Comparison)

| Feature | Windows Dynamic Lock | SignOff |
|---------|-----------------------|---------|
| **Connection** | Bluetooth Only | **Wi-Fi + BLE (Hybrid)** |
| **Sensitivity** | Fixed (Often slow) | **Customizable Thresholds** |
| **Motion Detection** | No | **Yes (Accelerometer Aware)** |
| **Dashboard** | None | **Live Web UI** |
| **Reliability** | Fails on some adapters | **Multiple Fallback Modes** |

## 🎯 Use Cases

- **Office Security**: Automatically lock your PC when you grab your phone to go for a coffee or meeting.
- **Privacy at Home**: Keep your workstation private from roommates or family without having to remember to lock it every time.
- **Public Spaces**: An extra layer of security when working in cafes or libraries.
- **Developer Workflows**: Seamless security that doesn't get in your way.


## 🛠️ Technology Stack

- **Backend (Desktop App)**:
  - Kotlin + Spring Boot (Java 23)
  - WebSockets (STOMP) for real-time bi-directional communication.
  - Native Windows API integration (`rundll32.exe user32.dll,LockWorkStation`) to trigger the screen lock.
- **Frontend Dashboard**:
  - Vanilla HTML, CSS, JavaScript using WebSockets.
- **Mobile App**:
  - Native Android (Kotlin).
  - Background Foreground Services, ConnectivityManager.
  - Device sensors (Accelerometer).
  - BLE Advertiser API.
- **Bluetooth Scanner**:
  - Python using the `bleak` library to scan and push RSSI data to the Spring Boot server.

## ⚙️ How It Works

1. The Spring Boot backend runs silently on your Windows machine, maintaining a WebSocket server.
2. The Android App establishes a connection (either over Wi-Fi or by broadcasting BLE signals).
3. The app continuously sends heartbeat packets containing accelerometer data (or the Python script sends RSSI data).
4. If the laptop stops receiving heartbeats, or the RSSI drops below a threshold *while* motion is detected, SignOff initiates a "Grace Period".
5. If the phone doesn't return within the grace period, the Windows machine is instantly locked.

## 💻 Setup Instructions

### Prerequisites
- Windows OS (for the `user32.dll` lock command).
- Java 23 installed.
- Python 3+ (if you wish to use the BLE scanner daemon).
- An Android device.

### Running the Desktop Backend
You can run the server directly using Gradle:
```bash
# Windows
./gradlew.bat bootRun
```
The dashboard will be available at `http://localhost:8080/`.

### Running the BLE Scanner (Optional)
If you want to use the Bluetooth Mode:
```bash
pip install bleak requests
python ble_scanner.py
```

### Building the Android App
Open the `android/` directory in Android Studio and build the APK, or build it using the command line:
```bash
cd android
./gradlew assembleDebug
```
Install the resulting APK onto your Android device. Make sure to grant the necessary notifications, background activity, and Bluetooth permissions!

## 🤝 Contributing

Contributions are welcome! Whether it's reporting a bug, suggesting a feature, or submitting a Pull Request:
1. Fork the project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
