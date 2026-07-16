import 'package:flutter/material.dart';

import '../services/setup_service.dart';
import 'home_page.dart';
import 'welcome_page.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  final SetupService setupService = SetupService();

  @override
  void initState() {
    super.initState();
    checkSetup();
  }

  Future<void> checkSetup() async {
    // Show splash screen for 2 seconds
    await Future.delayed(const Duration(seconds: 2));

    if (!mounted) return;

    bool completed = await setupService.isSetupCompleted();

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => completed
            ? const MyHomePage(title: "TraceHer")
            : const WelcomePage(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F9D58),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: const [
            Icon(
              Icons.shield,
              color: Colors.white,
              size: 100,
            ),
            SizedBox(height: 25),
            Text(
              "TraceHer",
              style: TextStyle(
                color: Colors.white,
                fontSize: 34,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 10),
            Text(
              "Your Personal Safety Companion",
              style: TextStyle(
                color: Colors.white70,
                fontSize: 17,
              ),
            ),
          ],
        ),
      ),
    );
  }
}