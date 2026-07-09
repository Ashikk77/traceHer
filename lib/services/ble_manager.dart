import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class BleManager {
  BleManager._();

  static final BleManager instance = BleManager._();

  static const String targetDeviceName = "traceHer";
  static const String serviceUuid = "12345678-1234-1234-1234-1234567890AB";
  static const String characteristicUuid =
      "87654321-4321-4321-4321-BA0987654321";

  BluetoothDevice? connectedDevice;
  BluetoothCharacteristic? notifyCharacteristic;

  Function(String message)? onMessageReceived;

  StreamSubscription<List<ScanResult>>? _scanSubscription;
  StreamSubscription<BluetoothConnectionState>? _connectionSubscription;
  StreamSubscription<List<int>>? _notifySubscription;

  bool _reconnecting = false;
  bool allowReconnect = false;

  Future<void> connect(BluetoothDevice device) async {
    connectedDevice = device;
    _reconnecting = false;
    allowReconnect = true;

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

    _connectionSubscription =
        device.connectionState.listen((state) async {
          debugPrint("Connection State : $state");

          if (state == BluetoothConnectionState.disconnected) {
            debugPrint("Device Disconnected");

            await reconnect();
          }
        });
  }

  Future<void> reconnect() async {
    if (!allowReconnect) {
      debugPrint("Reconnect disabled");
      return;
    }

    if (_reconnecting) {
      debugPrint("Reconnect already running");
      return;
    }

    _reconnecting = true;

    debugPrint("Starting Auto Reconnect...");

    await _scanSubscription?.cancel();

    _scanSubscription =
        FlutterBluePlus.scanResults.listen((results) async {
          for (final result in results) {
            if (result.device.platformName.toLowerCase() ==
                targetDeviceName.toLowerCase()) {
              debugPrint("TraceHer Found!");

              _reconnecting = false;

              await FlutterBluePlus.stopScan();

              await connect(result.device);

              return;
            }
          }
        });

    while (_reconnecting) {
      if (!allowReconnect) break;

      try {
        if (FlutterBluePlus.isScanningNow) {
          debugPrint("Scan already running");
          await Future.delayed(const Duration(seconds: 2));
          continue;
        }

        debugPrint("Searching for TraceHer...");

        await FlutterBluePlus.startScan(
          timeout: const Duration(seconds: 5),
        );

        await Future.delayed(const Duration(seconds: 6));
      } catch (e) {
        debugPrint("Reconnect Scan Error : $e");

        await Future.delayed(const Duration(seconds: 3));
      }
    }
  }

  Future<void> stopReconnect() async {
    debugPrint("Stopping BLE reconnect");

    allowReconnect = false;
    _reconnecting = false;

    await _scanSubscription?.cancel();

    try {
      await FlutterBluePlus.stopScan();
    } catch (_) {}
  }

  Future<void> stopScanning() async {
    _reconnecting = false;

    await _scanSubscription?.cancel();

    try {
      await FlutterBluePlus.stopScan();
    } catch (_) {}

    debugPrint("BLE Scan Stopped");
  }

  Future<void> disconnect() async {
    debugPrint("Disconnecting TraceHer...");

    allowReconnect = false;
    _reconnecting = false;

    await _scanSubscription?.cancel();
    _scanSubscription = null;

    await _notifySubscription?.cancel();
    _notifySubscription = null;

    await _connectionSubscription?.cancel();
    _connectionSubscription = null;

    try {
      await FlutterBluePlus.stopScan();
    } catch (_) {}

    if (connectedDevice != null) {
      try {
        await connectedDevice!.disconnect();
      } catch (_) {}
    }

    connectedDevice = null;
    notifyCharacteristic = null;
    onMessageReceived = null;

    debugPrint("Disconnected Successfully");
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

            await _notifySubscription?.cancel();

            _notifySubscription =
                characteristic.lastValueStream.listen((value) {
                  if (value.isEmpty) return;

                  String message = utf8.decode(value);

                  debugPrint("Received : $message");

                  onMessageReceived?.call(message);
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
    _scanSubscription?.cancel();
    _connectionSubscription?.cancel();
    _notifySubscription?.cancel();
  }
}