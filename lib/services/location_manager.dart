import 'dart:async';

import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';

class LocationManager {
  LocationManager._();

  static final LocationManager instance = LocationManager._();

  static const String _latitudeKey =
      'traceher_last_latitude';

  static const String _longitudeKey =
      'traceher_last_longitude';

  static const String _timeKey =
      'traceher_last_location_time';

  Position? _lastPosition;
  StreamSubscription<Position>? _subscription;

  Position? get lastPosition => _lastPosition;

  Future<void> _saveLocation(Position position) async {
    final prefs = await SharedPreferences.getInstance();

    await prefs.setString(
      _latitudeKey,
      position.latitude.toString(),
    );

    await prefs.setString(
      _longitudeKey,
      position.longitude.toString(),
    );

    await prefs.setInt(
      _timeKey,
      DateTime.now().millisecondsSinceEpoch,
    );

    print(
      "Location saved: "
          "${position.latitude}, ${position.longitude}",
    );
  }

  Future<void> startTracking() async {
    final serviceEnabled =
    await Geolocator.isLocationServiceEnabled();

    if (!serviceEnabled) {
      print("Location service disabled");
      return;
    }

    LocationPermission permission =
    await Geolocator.checkPermission();

    if (permission == LocationPermission.denied) {
      permission =
      await Geolocator.requestPermission();

      if (permission == LocationPermission.denied) {
        print("Location permission denied");
        return;
      }
    }

    if (permission == LocationPermission.deniedForever) {
      print("Location permission permanently denied");
      return;
    }

    try {
      _lastPosition =
      await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
        ),
      );

      print(
        "Initial Location: "
            "${_lastPosition!.latitude}, "
            "${_lastPosition!.longitude}",
      );

      await _saveLocation(_lastPosition!);
    } catch (e) {
      print("Initial location failed: $e");
    }

    await _subscription?.cancel();

    _subscription =
        Geolocator.getPositionStream(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high,
            distanceFilter: 10,
          ),
        ).listen(
              (Position position) async {
            _lastPosition = position;

            print(
              "Location Updated: "
                  "${position.latitude}, "
                  "${position.longitude}",
            );

            await _saveLocation(position);
          },
        );
  }

  void stopTracking() {
    _subscription?.cancel();
  }
}