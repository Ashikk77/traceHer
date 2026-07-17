import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/contact_service.dart';
import '../services/location_service.dart';
import '../services/sms_service.dart';
import 'emergency_contacts_page.dart';
import '../models/user_profile.dart';
import '../services/user_service.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final LocationService locationService = LocationService();
  final SmsService smsService = SmsService();
  final ContactService contactService = ContactService();
  final UserService userService = UserService();

  int contactCount = 0;
  UserProfile? userProfile;

  Position? currentPosition;
  bool loadingLocation = false;

  @override
  void initState() {
    super.initState();

    loadProfile();
    loadContacts();
    getCurrentLocation();
  }

  Future<void> loadProfile() async {
    final profile = await userService.getProfile();

    if (!mounted) return;

    setState(() {
      userProfile = profile;
    });
  }

  Future<void> loadContacts() async {
    final contacts = await contactService.getContacts();
    if (!mounted) return;

    setState(() {
      contactCount = contacts.length;
    });
  }

  Future<void> getCurrentLocation() async {

    setState(() {
      loadingLocation = true;
    });

    Position? position =
    await locationService.getCurrentLocation();

    if (!mounted) return;

    setState(() {
      currentPosition = position;
      loadingLocation = false;
    });

  }

  Future<void> sendTestSOS() async {
    PermissionStatus status = await Permission.sms.request();

    if (!status.isGranted) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("SMS Permission Denied")),
      );
      return;
    }

    Position? position = await locationService.getCurrentLocation();
    if (position == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Unable to get location")),
      );
      return;
    }

    final contacts = await contactService.getContacts();
    if (contacts.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("No Emergency Contacts")),
      );
      return;
    }

    String message =
        "🚨 TraceHer Test SOS\n\n"
        "This is a test emergency alert.\n\n"
        "Location:\n"
        "https://maps.google.com/?q=${position.latitude},${position.longitude}";

    for (final contact in contacts) {
      await smsService.sendSMS(
        phone: contact.phoneNumber,
        message: message,
      );
    }

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text("Test SOS Sent Successfully")),
    );
  }

  Widget statusTile(IconData icon, String title, String value, Color color) {
    return Expanded(
      child: Column(
        children: [
          Icon(icon, color: color, size: 32),
          const SizedBox(height: 10),
          Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
          ),
          Text(title, style: const TextStyle(color: Colors.grey)),
        ],
      ),
    );
  }

  Widget actionButton(IconData icon, String text, VoidCallback onTap) {
    return SizedBox(
      width: double.infinity,
      height: 55,
      child: ElevatedButton.icon(
        icon: Icon(icon),
        label: Text(text),
        onPressed: onTap,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey.shade100,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.green,
        foregroundColor: Colors.white,
        title: const Text(
          "traceHer",
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 24,
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: loadContacts,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(
              "Hi, ${userProfile?.name ?? "User"} 👋",
              style: const TextStyle(
                fontSize: 18,
                color: Colors.grey,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              "Your Safety Dashboard",
              style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 25),

            // Device Status Card
            Card(
              elevation: 4,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: [
                    Row(
                      children: const [
                        CircleAvatar(
                          radius: 22,
                          backgroundColor: Colors.green,
                          child: Icon(Icons.bluetooth_connected, color: Colors.white),
                        ),
                        SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text("Device Status", style: TextStyle(color: Colors.grey)),
                              SizedBox(height: 3),
                              Text(
                                "Connected",
                                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20),
                              ),
                            ],
                          ),
                        ),
                        Icon(Icons.check_circle, color: Colors.green, size: 30),
                      ],
                    ),
                    const Divider(height: 30),
                    Row(
                      children: [
                        statusTile(Icons.shield, "SOS", "Ready", Colors.green),
                        statusTile(Icons.people, "Contacts", "$contactCount", Colors.blue),
                        statusTile(Icons.battery_full, "Battery", "--", Colors.orange),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 25),

            // Location Service Card
            Card(
              elevation: 4,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [

                    const Text(
                      "📍 Current Location",
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),

                    const SizedBox(height: 15),

                    if (loadingLocation)
                      const Center(
                        child: CircularProgressIndicator(),
                      )
                    else if (currentPosition == null)
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [

                          Text(
                            "Location not available",
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                            ),
                          ),

                          SizedBox(height: 8),

                          Text(
                            "Tap Refresh to get your current location.",
                          ),

                        ],
                      )
                    else
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [

                          Text(
                            "Latitude : ${currentPosition!.latitude}",
                          ),

                          SizedBox(height: 5),

                          Text(
                            "Longitude : ${currentPosition!.longitude}",
                          ),

                        ],
                      ),

                    const SizedBox(height: 20),

                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: getCurrentLocation,
                        icon: const Icon(Icons.refresh),
                        label: const Text("Refresh Location"),
                      ),
                    ),

                  ],
                ),
              ),
            ),

            const SizedBox(height: 30),
            const Text("Quick Actions", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            const SizedBox(height: 15),

            actionButton(Icons.contacts, "Emergency Contacts", () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const EmergencyContactsPage()),
              );
              loadContacts();
            }),
            const SizedBox(height: 15),

            actionButton(Icons.warning_amber_rounded, "Test SOS", sendTestSOS),
            const SizedBox(height: 15),

            actionButton(Icons.settings, "Device Settings", () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text("Coming Soon")),
              );
            }),
          ],
        ),
      ),
    );
  }
}
