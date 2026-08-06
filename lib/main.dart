import 'package:flutter/material.dart';

import 'screens/splash_screen.dart';
import 'services/background_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Start Android Foreground Service
  await BackgroundService.start();

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