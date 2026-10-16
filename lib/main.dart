import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'screens/splash_screen.dart';
import 'services/background_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // ============================================================
  // ASK ONLY FOR BLUETOOTH / NEARBY DEVICES AT APP START
  // ============================================================
  // Do NOT ask for:
  // - Location
  // - SMS
  // - Contacts
  // - Notifications
  //
  // These permissions will be requested later when the user
  // connects the ESP32 device.
  // ============================================================

  await Permission.bluetoothScan.request();
  await Permission.bluetoothConnect.request();

  // ============================================================
  // START FOREGROUND BLE SERVICE
  // ============================================================

  final bluetoothScanGranted =
  await Permission.bluetoothScan.isGranted;

  final bluetoothConnectGranted =
  await Permission.bluetoothConnect.isGranted;

  if (bluetoothScanGranted && bluetoothConnectGranted) {
    await BackgroundService.start();
  } else {
    debugPrint(
      'TraceHer: Bluetooth permissions not granted. '
          'Foreground BLE service was not started.',
    );
  }

  // ============================================================
  // START FLUTTER APPLICATION
  // ============================================================

  runApp(const TraceHerApp());
}

class TraceHerApp extends StatelessWidget {
  const TraceHerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TraceHer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.green,
        ),
      ),
      home: const SplashScreen(),
    );
  }
}