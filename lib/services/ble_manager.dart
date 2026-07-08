import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class BleManager {
  BleManager._();

  static final BleManager instance = BleManager._();

  // Device Name
  static const String targetDeviceName = "TraceHer";

  // UUIDs
  static const String serviceUuid =
      "12345678-1234-1234-1234-1234567890AB";

  static const String characteristicUuid =
      "87654321-4321-4321-4321-BA0987654321";

  BluetoothDevice? connectedDevice;
  BluetoothCharacteristic? notifyCharacteristic;

  Function(String message)? onMessageReceived;

  StreamSubscription<List<ScanResult>>? _scanSubscription;
  StreamSubscription<BluetoothConnectionState>? _connectionSubscription;
  StreamSubscription<List<int>>? _notifySubscription;

  Timer? _reconnectTimer;
  bool _reconnecting = false;

  bool _isScanning = false;

  Future<void> connect(BluetoothDevice device) async {
    connectedDevice = device;
    _reconnecting = false;
    _reconnectTimer?.cancel();

    try {
      await device.connect(timeout: const Duration(seconds: 10));
    } catch (_) {
      // Already connected
    }

    debugPrint("Connected to ${device.platformName}");

    _listenConnection(device);

    await _discoverServices(device);
  }

  void _listenConnection(BluetoothDevice device) {
    _connectionSubscription?.cancel();

    _connectionSubscription = device.connectionState.listen((state) async {
      debugPrint("Connection State : $state");

      if (state == BluetoothConnectionState.disconnected) {
        debugPrint("Device Disconnected");

        await reconnect();
      }
    });
  }

  Future<void> reconnect() async {

    if (_reconnecting) return;

    _reconnecting = true;

    debugPrint("Starting Auto Reconnect...");

    _reconnectTimer?.cancel();

    _reconnectTimer = Timer.periodic(
      const Duration(seconds: 2),
          (timer) async {

        debugPrint("Searching for TraceHer...");

        await FlutterBluePlus.stopScan();

        await FlutterBluePlus.startScan(
          timeout: const Duration(seconds: 5),
        );
      },
    );

    _scanSubscription?.cancel();

    _scanSubscription =
        FlutterBluePlus.scanResults.listen((results) async {

          for (final result in results) {

            if (result.device.platformName == targetDeviceName) {

              debugPrint("TraceHer Found!");

              _reconnectTimer?.cancel();

              _reconnecting = false;

              await FlutterBluePlus.stopScan();

              await connect(result.device);

              break;
            }
          }
        });
  }

  Future<void> _discoverServices(BluetoothDevice device) async {
    debugPrint("Discovering Services...");

    List<BluetoothService> services =
    await device.discoverServices();

    for (BluetoothService service in services) {
      if (service.uuid.toString().toUpperCase() ==
          serviceUuid.toUpperCase()) {
        for (BluetoothCharacteristic characteristic
        in service.characteristics) {
          if (characteristic.uuid.toString().toUpperCase() ==
              characteristicUuid.toUpperCase()) {
            notifyCharacteristic = characteristic;

            await characteristic.setNotifyValue(true);

            _notifySubscription?.cancel();

            _notifySubscription =
                characteristic.lastValueStream.listen((value) {
                  if (value.isEmpty) return;

                  String message = utf8.decode(value);

                  debugPrint("Received : $message");

                  if (onMessageReceived != null) {
                    onMessageReceived!(message);
                  }
                });

            debugPrint("Notification Ready");

            return;
          }
        }
      }
    }

    debugPrint("Characteristic Not Found");
  }

  void dispose() {
    _reconnectTimer?.cancel();
    _scanSubscription?.cancel();
    _connectionSubscription?.cancel();
    _notifySubscription?.cancel();
  }
}