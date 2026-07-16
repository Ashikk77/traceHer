import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/user_profile.dart';

class UserService {

  static const String _key = "user_profile";

  Future<void> saveProfile(UserProfile profile) async {

    final prefs = await SharedPreferences.getInstance();

    await prefs.setString(
      _key,
      jsonEncode(profile.toJson()),
    );
  }

  Future<UserProfile?> getProfile() async {

    final prefs = await SharedPreferences.getInstance();

    final data = prefs.getString(_key);

    if (data == null) {
      return null;
    }

    return UserProfile.fromJson(
      jsonDecode(data),
    );
  }

  Future<bool> isProfileCreated() async {

    final prefs = await SharedPreferences.getInstance();

    return prefs.containsKey(_key);
  }
}