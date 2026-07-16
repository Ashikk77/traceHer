import 'package:flutter/material.dart';
import 'screens/splash_screen.dart';

void main() {
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