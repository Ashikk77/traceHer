import 'package:flutter/services.dart';

class SmsService {
  static const MethodChannel _channel = MethodChannel('traceher/sms');

  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    print("Calling native SMS...");
    print("Phone: $phone");
    print("Message: $message");

    try {
      final result = await _channel.invokeMethod('sendSMS', {
        'phone': phone,
        'message': message,
      });

      print("Native Result: $result");
    } on PlatformException catch (e) {
      print("SMS Error: ${e.message}");
    }
  }
}