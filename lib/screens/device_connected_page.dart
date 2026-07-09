import 'dart:async';

import 'package:flutter/material.dart';
import '../services/ble_manager.dart';

class DeviceConnectedPage extends StatefulWidget {
  const DeviceConnectedPage({super.key});

  @override
  State<DeviceConnectedPage> createState() => _DeviceConnectedPageState();
}

class _DeviceConnectedPageState extends State<DeviceConnectedPage> {
  String lastMessage = "Waiting for Emergency...";
  Timer? _resetTimer;

  @override
  void initState() {
    super.initState();

    BleManager.instance.onMessageReceived = (message) {
      if (!mounted) return;

      _resetTimer?.cancel();

      setState(() {
        lastMessage = message;
      });

      if (message == "SOS") {
        _resetTimer = Timer(const Duration(seconds: 5), () {
          if (!mounted) return;

          setState(() {
            lastMessage = "Waiting for Emergency...";
          });
        });
      }
    };
  }

  @override
  void dispose() {
    _resetTimer?.cancel();
    BleManager.instance.disconnect();
    super.dispose();
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