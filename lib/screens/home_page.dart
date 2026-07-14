import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';

import '../services/location_service.dart';
import '../services/sms_service.dart';
import 'emergency_contacts_page.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final LocationService locationService = LocationService();
  final SmsService smsService = SmsService();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text("Phase 1"),
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
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

              // Emergency Contacts Button
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) =>
                        const EmergencyContactsPage(),
                      ),
                    );
                  },
                  child: const Text("Emergency Contacts"),
                ),
              ),

              const SizedBox(height: 20),

              // Test GPS
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  icon: const Icon(Icons.location_on),
                  label: const Text("Test GPS"),
                  onPressed: () async {
                    Position? position =
                    await locationService.getCurrentLocation();

                    if (position == null) {
                      if (!context.mounted) return;

                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text("Unable to get location"),
                        ),
                      );
                      return;
                    }

                    String location =
                        "Latitude : ${position.latitude}\n\n"
                        "Longitude : ${position.longitude}\n\n"
                        "Google Maps:\n"
                        "https://maps.google.com/?q=${position.latitude},${position.longitude}";

                    if (!context.mounted) return;

                    showDialog(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: const Text("Current Location"),
                        content: SingleChildScrollView(
                          child: Text(location),
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context),
                            child: const Text("OK"),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),

              const SizedBox(height: 20),

              // Test SMS
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  icon: const Icon(Icons.sms),
                  label: const Text("Test SMS"),
                  onPressed: () async {
                    PermissionStatus status =
                    await Permission.sms.request();

                    if (!status.isGranted) {
                      if (!context.mounted) return;

                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text("SMS Permission Denied"),
                        ),
                      );
                      return;
                    }

                    await smsService.sendSMS(
                      phone: "7736172422",
                      message:
                      "🚨 TraceHer Test 🚨\n\nThis is an automatic SMS sent from TraceHer.",
                    );

                    if (!context.mounted) return;

                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text("SMS Request Sent"),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}