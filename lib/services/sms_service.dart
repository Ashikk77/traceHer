import 'package:flutter/services.dart';

class SmsService {
  static const MethodChannel _channel = MethodChannel('traceher/sms');

  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    try {
      final result = await _channel.invokeMethod('sendSMS', {
        'phone': phone,
        'message': message,
      });

      print(result);
    } on PlatformException catch (e) {
      print("SMS Error: ${e.message}");
    }
  }
}