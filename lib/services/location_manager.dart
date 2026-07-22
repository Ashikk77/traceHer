import 'dart:async';
import 'package:geolocator/geolocator.dart';

class LocationManager {
  LocationManager._();

  static final LocationManager instance = LocationManager._();

  Position? _lastPosition;
  StreamSubscription<Position>? _subscription;

  Position? get lastPosition => _lastPosition;

  Future<void> startTracking() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();

    if (!serviceEnabled) {
      print("Location service disabled");
      return;
    }

    LocationPermission permission = await Geolocator.checkPermission();

    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();

      if (permission == LocationPermission.denied) {
        print("Location permission denied");
        return;
      }
    }

    if (permission == LocationPermission.deniedForever) {
      print("Location permission permanently denied");
      return;
    }

    // Get one location immediately
    try {
      _lastPosition = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
        ),
      );

      print(
          "Initial Location: ${_lastPosition!.latitude}, ${_lastPosition!.longitude}");
    } catch (e) {
      print("Initial location failed: $e");
    }

    // Listen for future updates
    _subscription?.cancel();

    _subscription = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 10,
      ),
    ).listen((Position position) {
      _lastPosition = position;

      print(
          "Location Updated: ${position.latitude}, ${position.longitude}");
    });
  }

  void stopTracking() {
    _subscription?.cancel();
  }
}