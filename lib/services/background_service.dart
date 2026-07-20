import 'package:flutter/services.dart';

class BackgroundService {
  static const MethodChannel _channel =
  MethodChannel("traceher/service");

  static Future<void> start() async {
    try {
      await _channel.invokeMethod("startService");
    } catch (e) {
      print("Background Service Error: $e");
    }
  }
}