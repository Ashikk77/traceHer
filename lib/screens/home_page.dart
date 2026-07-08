import 'package:flutter/material.dart';
import 'device_scan_page.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text("Phase 1"),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.bluetooth_searching,
              size: 100,
              color: Colors.blue,
            ),

            const SizedBox(height: 20),

            const Text(
              "Searching for Device",
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 10),

            const Text(
              "Waiting for ESP32 connection",
            ),

            const SizedBox(height: 30),

            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const DeviceScanPage(),
                  ),
                );
              },
              child: const Text("Scan"),
            ),
          ],
        ),
      ),
    );
  }
}