import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';

import '../services/ble_manager.dart';
import 'device_connected_page.dart';

class DeviceScanPage extends StatefulWidget {
  const DeviceScanPage({super.key});

  @override
  State<DeviceScanPage> createState() => _DeviceScanPageState();
}

class _DeviceScanPageState extends State<DeviceScanPage> {
  final BleManager bleManager = BleManager.instance;

  List<ScanResult> scanResults = [];

  @override
  void initState() {
    super.initState();
    startScan();
  }

  Future<void> startScan() async {
    if (!await FlutterBluePlus.isSupported) {
      debugPrint("Bluetooth not supported");
      return;
    }

    await Permission.bluetoothScan.request();
    await Permission.bluetoothConnect.request();
    await Permission.location.request();

    await FlutterBluePlus.startScan(
      timeout: const Duration(seconds: 5),
    );

    FlutterBluePlus.scanResults.listen((results) {
      if (!mounted) return;

      setState(() {
        scanResults = results;
      });

      debugPrint("Devices Found: ${results.length}");
    });
  }

  @override
  void dispose() {
    FlutterBluePlus.stopScan();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Scan Devices"),
        centerTitle: true,
      ),
      body: ListView.builder(
        itemCount: scanResults.length,
        itemBuilder: (context, index) {
          final device = scanResults[index].device;

          return Card(
            margin: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 6,
            ),
            child: ListTile(
              leading: const Icon(
                Icons.bluetooth,
                color: Colors.blue,
              ),
              title: Text(
                device.platformName.isNotEmpty
                    ? device.platformName
                    : "Unknown Device",
              ),
              subtitle: Text(device.remoteId.toString()),
              trailing: ElevatedButton(
                child: const Text("Connect"),
                onPressed: () async {
                  if (!mounted) return;

                  final navigator = Navigator.of(context);
                  final messenger = ScaffoldMessenger.of(context);

                  try {
                    await bleManager.connect(device);

                    if (!mounted) return;

                    navigator.pushReplacement(
                      MaterialPageRoute(
                        builder: (_) => const DeviceConnectedPage(),
                      ),
                    );

                    messenger.showSnackBar(
                      SnackBar(
                        content: Text(
                          "${device.platformName} Connected",
                        ),
                      ),
                    );
                  } catch (e) {
                    messenger.showSnackBar(
                      SnackBar(
                        content: Text(
                          "Connection Failed\n$e",
                        ),
                      ),
                    );
                  }
                },
              ),
            ),
          );
        },
      ),
    );
  }
}