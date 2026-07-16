import 'package:flutter/material.dart';

import 'device_scan_page.dart';

class DeviceSetupPage extends StatelessWidget {
  const DeviceSetupPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Step 3 of 4"),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            const SizedBox(height: 20),

            const Icon(
              Icons.bluetooth_searching,
              size: 120,
              color: Colors.blue,
            ),

            const SizedBox(height: 30),

            const Text(
              "Setup Your TraceHer Device",
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 25),

            const Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  "1. Switch ON your TraceHer device.\n\n"
                      "2. Wait for 3–5 seconds.\n\n"
                      "3. Press the button below to scan nearby devices.\n\n"
                      "4. Select your TraceHer device and connect.",
                  style: TextStyle(fontSize: 16),
                ),
              ),
            ),

            const Spacer(),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(Icons.bluetooth_searching),
                label: const Text("Scan Device"),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const DeviceScanPage(),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}