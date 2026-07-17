import 'package:flutter/services.dart';

class SmsService {

  static const MethodChannel _channel =
  MethodChannel('traceher/sms_v2');


  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {

    print("Calling V2 SMS...");
    print("Phone: $phone");
    print("Message: $message");


    try {

      final result = await _channel.invokeMethod(
        'sendSMSV2',
        {
          'phone': phone,
          'message': message,
        },
      );


      print("V2 Result: $result");


    } on PlatformException catch(e) {

      print("V2 Error: ${e.message}");

    }
  }
}