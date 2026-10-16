import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class BackgroundService {
  static const MethodChannel _channel =
  MethodChannel("traceher/service");

  static bool _serviceStartRequested = false;

  static Future<void> start() async {
    if (_serviceStartRequested) {
      debugPrint(
        "TraceHer: Background service start already requested",
      );
      return;
    }

    _serviceStartRequested = true;

    try {
      await _channel.invokeMethod("startService");

      debugPrint(
        "TraceHer: Background service started",
      );
    } catch (e) {
      // Allow another attempt if this request actually failed.
      _serviceStartRequested = false;

      debugPrint(
        "Background Service Error: $e",
      );
    }
  }

  static Future<void> startBleMonitoring() async {
    try {
      await _channel.invokeMethod("startBleMonitoring");

      debugPrint(
        "TraceHer: Native BLE monitoring requested",
      );
    } catch (e) {
      debugPrint(
        "Native BLE Monitoring Error: $e",
      );
    }
  }
}