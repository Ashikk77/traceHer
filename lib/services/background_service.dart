import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class BackgroundService {
  static const MethodChannel _channel =
  MethodChannel("traceher/service");

  static Future<void> start() async {
    try {
      await _channel.invokeMethod("startService");
    } catch (e) {
      debugPrint("Background Service Error: $e");
    }
  }

  static Future<void> startBleMonitoring() async {
    try {
      await _channel.invokeMethod("startBleMonitoring");
      debugPrint("TraceHer: Native BLE monitoring requested");
    } catch (e) {
      debugPrint("Native BLE Monitoring Error: $e");
    }
  }
}