import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'device_setup_page.dart';

class PermissionsPage extends StatefulWidget {
  const PermissionsPage({super.key});

  @override
  State<PermissionsPage> createState() => _PermissionsPageState();
}

class _PermissionsPageState extends State<PermissionsPage> {

  Future<void> requestPermissions() async {

    await [
      Permission.location,
      Permission.sms,
      Permission.bluetooth,
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
      Permission.contacts,
    ].request();

    if (!mounted) return;

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => const DeviceSetupPage(),
      ),
    );
  }

  Widget permissionTile(
      IconData icon,
      String title,
      String subtitle,
      ) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: Colors.green),
        title: Text(title),
        subtitle: Text(subtitle),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      appBar: AppBar(
        title: const Text("Step 2 of 4"),
        centerTitle: true,
      ),

      resizeToAvoidBottomInset: false,

      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(

            children: [

              const SizedBox(height: 20),

              const Icon(
                Icons.security,
                size: 80,
                color: Colors.green,
              ),

              const SizedBox(height: 20),

              const Text(
                "Permissions Required",
                style: TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                ),
              ),

              const SizedBox(height: 10),

              const Text(
                "TraceHer needs the following permissions to keep you safe.",
                textAlign: TextAlign.center,
              ),

              const SizedBox(height: 30),

              permissionTile(
                Icons.location_on,
                "Location",
                "Share live location during emergencies.",
              ),

              permissionTile(
                Icons.sms,
                "SMS",
                "Send emergency messages.",
              ),

              permissionTile(
                Icons.bluetooth,
                "Bluetooth",
                "Connect to your TraceHer device.",
              ),

              permissionTile(
                Icons.contacts,
                "Contacts",
                "Import emergency contacts.",
              ),

              const Spacer(),

              SizedBox(
                width: double.infinity,
                height: 55,
                child: ElevatedButton(
                  onPressed: requestPermissions,
                  child: const Text(
                    "Grant Permissions",
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}