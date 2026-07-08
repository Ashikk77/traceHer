import 'package:flutter/material.dart';
import '../services/ble_manager.dart';

class DeviceConnectedPage extends StatefulWidget {
  const DeviceConnectedPage({super.key});

  @override
  State<DeviceConnectedPage> createState() => _DeviceConnectedPageState();
}

class _DeviceConnectedPageState extends State<DeviceConnectedPage> {
  String lastMessage = "Waiting for Emergency...";

  @override
  void initState() {
    super.initState();

    // Listen for messages from ESP32
    BleManager.instance.onMessageReceived = (message) {
      if (!mounted) return;

      setState(() {
        lastMessage = message;
      });
    };
  }

  @override
  Widget build(BuildContext context) {
    final device = BleManager.instance.connectedDevice;

    return Scaffold(
      appBar: AppBar(
        title: const Text("TraceHer"),
        centerTitle: true,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.bluetooth_connected,
                color: Colors.green,
                size: 100,
              ),

              const SizedBox(height: 20),

              Text(
                device?.platformName ?? "TraceHer",
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),

              const SizedBox(height: 10),

              const Text(
                "Device Connected Successfully",
                style: TextStyle(
                  color: Colors.green,
                  fontSize: 18,
                ),
              ),

              const SizedBox(height: 40),

              Card(
                child: ListTile(
                  leading: const Icon(Icons.emergency),
                  title: const Text("SOS Status"),
                  subtitle: Text(
                    lastMessage,
                    style: TextStyle(
                      color: lastMessage == "SOS"
                          ? Colors.red
                          : Colors.green,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 10),

              const Card(
                child: ListTile(
                  leading: Icon(Icons.battery_full),
                  title: Text("Battery"),
                  subtitle: Text("Coming Soon"),
                ),
              ),

              const SizedBox(height: 10),

              const Card(
                child: ListTile(
                  leading: Icon(Icons.location_on),
                  title: Text("Location"),
                  subtitle: Text("Ready"),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}