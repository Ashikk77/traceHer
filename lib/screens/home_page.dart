import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';

import '../services/contact_service.dart';
import '../services/location_service.dart';
import 'emergency_contacts_page.dart';
import '../models/user_profile.dart';
import '../services/user_service.dart';
import '../services/sms_service.dart';
import '../services/ble_manager.dart';
import '../services/location_manager.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage>
    with WidgetsBindingObserver {
  final LocationService locationService = LocationService();
  final SmsService smsService = SmsService();
  final ContactService contactService = ContactService();
  final UserService userService = UserService();

  int contactCount = 0;
  UserProfile? userProfile;

  Position? currentPosition;
  bool loadingLocation = false;

  // Actual BLE status from native foreground service
  bool deviceConnected = false;

  // Correct native service channel
  static const MethodChannel serviceChannel =
  MethodChannel('traceher/service');

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addObserver(this);

    _initialize();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // ==========================================================
  // APP LIFECYCLE
  // ==========================================================

  @override
  void didChangeAppLifecycleState(
      AppLifecycleState state) {

    if (state == AppLifecycleState.resumed) {
      debugPrint(
        "App resumed - checking native BLE status",
      );

      checkNativeBleConnection();
    }
  }

  // ==========================================================
  // INITIALIZE
  // ==========================================================

  Future<void> _initialize() async {

    // Start reconnect in background
    BleManager.instance.loadSavedDevice();

    // Start GPS tracking
    await LocationManager.instance.startTracking();

    loadProfile();
    loadContacts();
    getCurrentLocation();

    // Check actual BLE status from native service
    await checkNativeBleConnection();

    Future.delayed(
      const Duration(seconds: 1),
          () {

        if (!BleManager.instance.isConnected) {
          BleManager.instance.loadSavedDevice();
        }

        // Check native status again after reconnect attempt
        checkNativeBleConnection();
      },
    );

    // BLE message received by Flutter BLE manager
    BleManager.instance.onMessageReceived =
        (message) async {

      if (message.trim() == "SOS") {

        debugPrint(
          "HomePage received SOS",
        );

        await testSOS();
      }
    };

    // Flutter BLE connection changed
    BleManager.instance.onConnectionChanged =
        () {

      if (!mounted) return;

      // Do not directly trust the Flutter BLE state.
      // Ask the native foreground service for the
      // actual BLE connection status.
      checkNativeBleConnection();
    };
  }

  // ==========================================================
  // CHECK NATIVE BLE CONNECTION
  // ==========================================================

  Future<void> checkNativeBleConnection() async {

    try {

      final bool connected =
          await serviceChannel.invokeMethod<bool>(
            'isBleConnected',
          ) ??
              false;

      debugPrint(
        "Native foreground service BLE status: $connected",
      );

      if (!mounted) return;

      setState(() {
        deviceConnected = connected;
      });

    } catch (e) {

      debugPrint(
        "Error checking native BLE connection: $e",
      );

      if (!mounted) return;

      setState(() {
        deviceConnected = false;
      });
    }
  }

  // ==========================================================
  // LOAD PROFILE
  // ==========================================================

  Future<void> loadProfile() async {

    final profile =
    await userService.getProfile();

    if (!mounted) return;

    setState(() {
      userProfile = profile;
    });
  }

  // ==========================================================
  // LOAD CONTACTS
  // ==========================================================

  Future<void> loadContacts() async {

    final contacts =
    await contactService.getContacts();

    if (!mounted) return;

    setState(() {
      contactCount = contacts.length;
    });
  }

  // ==========================================================
  // GET CURRENT LOCATION
  // ==========================================================

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

  // ==========================================================
  // TEST SOS
  // ==========================================================

  Future<void> testSOS() async {

    debugPrint("STEP 1");

    // Get cached GPS location
    Position? position =
        LocationManager.instance.lastPosition;

    debugPrint(
      "Cached Location: $position",
    );

    String mapLink;

    if (position != null) {

      mapLink =
      "https://maps.google.com/?q="
          "${position.latitude},"
          "${position.longitude}";

    } else {

      mapLink =
      "Location unavailable";
    }

    // Load saved contacts
    final contacts =
    await contactService.getContacts();

    if (contacts.isEmpty) {

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content:
          Text(
            "No emergency contacts saved.",
          ),
        ),
      );

      return;
    }

    // Send SMS to every contact
    for (final contact in contacts) {

      debugPrint(
        "Sending SMS to: "
            "${contact.phoneNumber}",
      );

      await smsService.sendSMS(
        phone: contact.phoneNumber,
        message:
        "🚨 TraceHer SOS Test Alert\n\n"
            "I need immediate assistance.\n\n"
            "Location:\n"
            "$mapLink",
      );
    }

    debugPrint("STEP 2");

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content:
        Text(
          "SOS Test Sent Successfully",
        ),
      ),
    );

    debugPrint("STEP 3");
  }

  // ==========================================================
  // STATUS TILE
  // ==========================================================

  Widget statusTile(
      IconData icon,
      String title,
      String value,
      Color color,
      ) {

    return Expanded(
      child: Column(
        children: [

          Icon(
            icon,
            color: color,
            size: 32,
          ),

          const SizedBox(height: 10),

          Text(
            value,
            style: const TextStyle(
              fontWeight:
              FontWeight.bold,
              fontSize: 18,
            ),
          ),

          Text(
            title,
            style: const TextStyle(
              color: Colors.grey,
            ),
          ),
        ],
      ),
    );
  }

  // ==========================================================
  // ACTION BUTTON
  // ==========================================================

  Widget actionButton(
      IconData icon,
      String text,
      VoidCallback onTap,
      ) {

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

  // ==========================================================
  // BUILD
  // ==========================================================

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      backgroundColor:
      Colors.grey.shade100,

      // ======================================================
      // APP BAR
      // ======================================================

      appBar: AppBar(
        elevation: 0,

        backgroundColor:
        Colors.green,

        foregroundColor:
        Colors.white,

        title: const Text(
          "traceHer",

          style: TextStyle(
            fontWeight:
            FontWeight.bold,

            fontSize: 24,
          ),
        ),
      ),

      // ======================================================
      // BODY
      // ======================================================

      body: RefreshIndicator(
        onRefresh: loadContacts,

        child: ListView(
          padding:
          const EdgeInsets.all(20),

          children: [

            // ==================================================
            // GREETING
            // ==================================================

            Text(
              "Hi, "
                  "${userProfile?.name ?? "User"} 👋",

              style: const TextStyle(
                fontSize: 18,
                color: Colors.grey,
              ),
            ),

            const SizedBox(height: 6),

            const Text(
              "Your Safety Dashboard",

              style: TextStyle(
                fontSize: 30,
                fontWeight:
                FontWeight.bold,
              ),
            ),

            const SizedBox(height: 25),

            // ==================================================
            // DEVICE STATUS CARD
            // ==================================================

            Card(
              elevation: 4,

              shape:
              RoundedRectangleBorder(
                borderRadius:
                BorderRadius.circular(18),
              ),

              child: Padding(
                padding:
                const EdgeInsets.all(20),

                child: Column(
                  children: [

                    Row(
                      children: [

                        // --------------------------------------
                        // BLUETOOTH ICON
                        // --------------------------------------

                        CircleAvatar(
                          radius: 22,

                          backgroundColor:
                          deviceConnected
                              ? Colors.green
                              : Colors.red,

                          child: Icon(
                            deviceConnected
                                ? Icons
                                .bluetooth_connected
                                : Icons
                                .bluetooth_disabled,

                            color:
                            Colors.white,
                          ),
                        ),

                        const SizedBox(width: 15),

                        // --------------------------------------
                        // STATUS TEXT
                        // --------------------------------------

                        Expanded(
                          child: Column(
                            crossAxisAlignment:
                            CrossAxisAlignment.start,

                            children: [

                              const Text(
                                "Device Status",

                                style:
                                TextStyle(
                                  color:
                                  Colors.grey,
                                ),
                              ),

                              const SizedBox(
                                height: 3,
                              ),

                              Text(
                                deviceConnected
                                    ? "Connected"
                                    : "Disconnected",

                                style:
                                TextStyle(
                                  fontWeight:
                                  FontWeight.bold,

                                  fontSize: 20,

                                  color:
                                  deviceConnected
                                      ? Colors.green
                                      : Colors.red,
                                ),
                              ),
                            ],
                          ),
                        ),

                        // --------------------------------------
                        // STATUS ICON
                        // --------------------------------------

                        Icon(
                          deviceConnected
                              ? Icons.check_circle
                              : Icons.cancel,

                          color:
                          deviceConnected
                              ? Colors.green
                              : Colors.red,

                          size: 30,
                        ),
                      ],
                    ),

                    const Divider(
                      height: 30,
                    ),

                    // ------------------------------------------
                    // STATUS TILES
                    // ------------------------------------------

                    Row(
                      children: [

                        statusTile(
                          Icons.shield,
                          "SOS",
                          "Ready",
                          Colors.green,
                        ),

                        statusTile(
                          Icons.people,
                          "Contacts",
                          "$contactCount",
                          Colors.blue,
                        ),

                        statusTile(
                          Icons.battery_full,
                          "Battery",
                          "--",
                          Colors.orange,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 25),

            // ==================================================
            // LOCATION CARD
            // ==================================================

            Card(
              elevation: 4,

              shape:
              RoundedRectangleBorder(
                borderRadius:
                BorderRadius.circular(18),
              ),

              child: Padding(
                padding:
                const EdgeInsets.all(20),

                child: Column(
                  crossAxisAlignment:
                  CrossAxisAlignment.start,

                  children: [

                    const Text(
                      "📍 Current Location",

                      style: TextStyle(
                        fontSize: 18,
                        fontWeight:
                        FontWeight.bold,
                      ),
                    ),

                    const SizedBox(height: 15),

                    // ------------------------------------------
                    // LOCATION STATUS
                    // ------------------------------------------

                    if (loadingLocation)

                      const Center(
                        child:
                        CircularProgressIndicator(),
                      )

                    else if (
                    currentPosition == null
                    )

                      const Column(
                        crossAxisAlignment:
                        CrossAxisAlignment.start,

                        children: [

                          Text(
                            "Location not available",

                            style: TextStyle(
                              fontWeight:
                              FontWeight.bold,
                            ),
                          ),

                          SizedBox(height: 8),

                          Text(
                            "Tap Refresh to get "
                                "your current location.",
                          ),
                        ],
                      )

                    else

                      Column(
                        crossAxisAlignment:
                        CrossAxisAlignment.start,

                        children: [

                          Text(
                            "Latitude : "
                                "${currentPosition!.latitude}",
                          ),

                          const SizedBox(
                            height: 5,
                          ),

                          Text(
                            "Longitude : "
                                "${currentPosition!.longitude}",
                          ),
                        ],
                      ),

                    const SizedBox(height: 20),

                    // ------------------------------------------
                    // REFRESH LOCATION
                    // ------------------------------------------

                    SizedBox(
                      width:
                      double.infinity,

                      child:
                      ElevatedButton.icon(
                        onPressed:
                        getCurrentLocation,

                        icon:
                        const Icon(
                          Icons.refresh,
                        ),

                        label:
                        const Text(
                          "Refresh Location",
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 30),

            // ==================================================
            // QUICK ACTIONS
            // ==================================================

            const Text(
              "Quick Actions",

              style: TextStyle(
                fontWeight:
                FontWeight.bold,
                fontSize: 18,
              ),
            ),

            const SizedBox(height: 15),

            // ==================================================
            // EMERGENCY CONTACTS
            // ==================================================

            actionButton(
              Icons.contacts,
              "Emergency Contacts",

                  () async {

                await Navigator.push(
                  context,

                  MaterialPageRoute(
                    builder: (_) =>
                    const EmergencyContactsPage(),
                  ),
                );

                loadContacts();

                // Check native BLE status
                // when returning to dashboard
                checkNativeBleConnection();
              },
            ),

            const SizedBox(height: 15),

            // ==================================================
            // DEVICE SETTINGS
            // ==================================================

            actionButton(
              Icons.settings,
              "Device Settings",

                  () {

                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(
                  const SnackBar(
                    content:
                    Text(
                      "Coming Soon",
                    ),
                  ),
                );
              },
            ),

            const SizedBox(height: 15),

            // ==================================================
            // TEST SOS
            // ==================================================

            actionButton(
              Icons.warning_amber_rounded,
              "Test SOS",
              testSOS,
            ),
          ],
        ),
      ),
    );
  }
}