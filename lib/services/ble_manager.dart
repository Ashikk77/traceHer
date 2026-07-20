import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'location_service.dart';
import 'contact_service.dart';
import 'sms_service.dart';

class BleManager {
  BleManager._();

  static final BleManager instance = BleManager._();

  final LocationService _locationService = LocationService();
  final ContactService _contactService = ContactService();
  final SmsService _smsService = SmsService();

  static const String targetDeviceName = "traceHer";

  static const String serviceUuid = "12345678-1234-1234-1234-1234567890AB";
  static const String characteristicUuid = "87654321-4321-4321-4321-BA0987654321";

  BluetoothDevice? connectedDevice;
  BluetoothCharacteristic? notifyCharacteristic;

  String? savedDeviceId;

  Function(String message)? onMessageReceived;
  VoidCallback? onConnectionChanged;

  StreamSubscription<List<ScanResult>>? _scanSubscription;
  StreamSubscription<BluetoothConnectionState>? _connectionSubscription;
  StreamSubscription<List<int>>? _notifySubscription;

  bool _reconnecting = false;
  bool allowReconnect = false;
  bool isConnected = false;
  bool _loadingSavedDevice = false;
  Completer<bool>? _connectionCompleter;

  // ==============================
  // SAVE BLE DEVICE
  // ==============================
  Future<void> saveDevice(String deviceId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString("saved_ble_device", deviceId);
    savedDeviceId = deviceId;
    debugPrint("Saved BLE Device: $deviceId");
  }

  // ==============================
  // DISCOVER SERVICES
  // ==============================
  Future<void> _discoverServices(BluetoothDevice device) async {
    debugPrint("Discovering Services...");
    List<BluetoothService> services = await device.discoverServices();

    for (BluetoothService service in services) {
      if (service.uuid.toString().toUpperCase() == serviceUuid.toUpperCase()) {
        for (BluetoothCharacteristic characteristic in service.characteristics) {
          if (characteristic.uuid.toString().toUpperCase() == characteristicUuid.toUpperCase()) {
            notifyCharacteristic = characteristic;

            await characteristic.setNotifyValue(true);
            await _notifySubscription?.cancel();

            _notifySubscription = characteristic.lastValueStream.listen((value) {
              if (value.isEmpty) return;
              String message = utf8.decode(value).trim();
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

  // ==============================
  // DISCONNECT
  // ==============================
  Future<void> disconnect() async {
    debugPrint("Disconnecting TraceHer...");
    allowReconnect = false;
    _reconnecting = false;

    await _scanSubscription?.cancel();
    await _connectionSubscription?.cancel();
    await _notifySubscription?.cancel();

    try {
      await FlutterBluePlus.stopScan();
    } catch (_) {}

    if (connectedDevice != null) {
      try {
        await connectedDevice!.disconnect();
      } catch (_) {}
    }

    isConnected = false;
    onConnectionChanged?.call();

    connectedDevice = null;
    notifyCharacteristic = null;

    debugPrint("Disconnected Successfully");
  }

  // ==============================
  // GET SAVED DEVICE
  // ==============================
  Future<String?> getSavedDevice() async {
    final prefs = await SharedPreferences.getInstance();
    final id = prefs.getString("saved_ble_device");
    debugPrint("Saved Device Loaded: $id");
    return id;
  }

// ==============================
// LOAD SAVED DEVICE
// ==============================
  Future<void> loadSavedDevice() async {

    if (_loadingSavedDevice) return;

    _loadingSavedDevice = true;

    final savedId = await getSavedDevice();

    if (savedId == null) {
      debugPrint("No saved device found");
      _loadingSavedDevice = false;
      return;
    }

    savedDeviceId = savedId;

    debugPrint("Trying saved device: $savedId");

    await reconnectToSavedDevice(savedId);

    _loadingSavedDevice = false;
  }

  Future<bool> waitForConnection() async {

    if(isConnected){
      return true;
    }

    _connectionCompleter = Completer<bool>();

    return await _connectionCompleter!.future
        .timeout(
      const Duration(seconds:15),
      onTimeout: (){
        return false;
      },
    );
  }

// ==============================
// RECONNECT SAVED DEVICE
// ==============================
  Future<void> reconnectToSavedDevice(String deviceId) async {
    debugPrint("Searching saved device...");

    await FlutterBluePlus.stopScan();

    StreamSubscription<List<ScanResult>>? subscription;

    subscription = FlutterBluePlus.scanResults.listen((results) async {
      for (final result in results) {

        debugPrint(
            "Found: ${result.device.platformName} "
                "${result.device.remoteId.str}");

        if (result.device.remoteId.str == deviceId) {

          debugPrint("Saved device found!");

          await FlutterBluePlus.stopScan();

          await subscription?.cancel();

          await connect(result.device);

          return;
        }
      }
    });

    await FlutterBluePlus.startScan(
      timeout: const Duration(seconds: 10),
    );
  }

  // ==============================
  // CONNECT DEVICE
  // ==============================
  Future<void> connect(BluetoothDevice device) async {
    connectedDevice = device;
    _reconnecting = false;
    allowReconnect = true;

    try {
      await device.connect(
        timeout: const Duration(seconds: 10),
        autoConnect: false,
      );
    } catch (e) {
      debugPrint("Connect error: $e");
    }

    debugPrint("Connected to ${device.platformName}");
    await saveDevice(device.remoteId.str);

    isConnected = true;

    _listenConnection(device);

    await _discoverServices(device);

    _connectionCompleter?.complete(true);

    onConnectionChanged?.call();
    debugPrint("BLE Connected");
  }

  // ==============================
  // CONNECTION LISTENER
  // ==============================
  void _listenConnection(BluetoothDevice device) {
    _connectionSubscription?.cancel();
    _connectionSubscription = device.connectionState.listen((state) async {
      debugPrint("Connection State : $state");

      if (state == BluetoothConnectionState.connected) {
        isConnected = true;
        onConnectionChanged?.call();
        debugPrint("BLE Connected");
      }

      if (state == BluetoothConnectionState.disconnected) {
        isConnected = false;
        onConnectionChanged?.call();
        debugPrint("BLE Disconnected");
        await reconnect();
      }
    });
  }

  // ==============================
  // AUTO RECONNECT
  // ==============================
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

    _scanSubscription = FlutterBluePlus.scanResults.listen((results) async {
      for (final result in results) {
        if (result.device.platformName.toLowerCase() == targetDeviceName.toLowerCase()) {
          debugPrint("TraceHer Found!");
          _reconnecting = false;
          await FlutterBluePlus.stopScan();
          await connect(result.device);
          return;
        }
      }
    });

    while (_reconnecting) {
      try {
        if (FlutterBluePlus.isScanningNow) {
          await Future.delayed(const Duration(seconds: 2));
          continue;
        }

        debugPrint("Searching for TraceHer...");
        await FlutterBluePlus.startScan(timeout: const Duration(seconds: 5));
        await Future.delayed(const Duration(seconds: 6));
      } catch (e) {
        debugPrint("Reconnect Error : $e");
      }
    }
  }
}
