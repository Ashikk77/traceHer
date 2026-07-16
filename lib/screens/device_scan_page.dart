import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';

import '../services/ble_manager.dart';

import '../services/setup_service.dart';
import 'home_page.dart';

class DeviceScanPage extends StatefulWidget {
  const DeviceScanPage({super.key});

  @override
  State<DeviceScanPage> createState() => _DeviceScanPageState();
}

class _DeviceScanPageState extends State<DeviceScanPage> {

  final BleManager bleManager = BleManager.instance;

  List<ScanResult> scanResults = [];

  StreamSubscription<List<ScanResult>>? scanSubscription;

  bool isScanning = false;


  @override
  void initState() {
    super.initState();
    startScan();
  }


  Future<void> startScan() async {

    if (isScanning) return;


    isScanning = true;


    if (mounted) {
      setState(() {
        scanResults.clear();
      });
    }


    try {

      if (!await FlutterBluePlus.isSupported) {
        debugPrint("Bluetooth not supported");
        return;
      }


      BluetoothAdapterState state =
      await FlutterBluePlus.adapterState.first;


      if (state != BluetoothAdapterState.on) {

        if (!mounted) return;


        await showDialog(
          context: context,
          builder: (context) {

            return AlertDialog(
              title: const Text(
                "Bluetooth Required",
              ),

              content: const Text(
                "Please turn ON Bluetooth to scan for TraceHer.",
              ),

              actions: [

                TextButton(
                  onPressed: () async {

                    Navigator.pop(context);

                    await FlutterBluePlus.turnOn();

                    startScan();

                  },

                  child: const Text(
                    "Turn ON",
                  ),
                ),

              ],
            );

          },
        );

        return;
      }


      await Permission.bluetoothScan.request();
      await Permission.bluetoothConnect.request();
      await Permission.location.request();


      await FlutterBluePlus.stopScan();


      await scanSubscription?.cancel();


      scanSubscription =
          FlutterBluePlus.scanResults.listen((results) {

            if (!mounted) return;


            setState(() {

              scanResults = results;

            });


          });


      debugPrint(
        "Scanning started",
      );


      await FlutterBluePlus.startScan(
        timeout: const Duration(seconds: 5),
      );


      debugPrint(
        "Scanning finished",
      );


    }

    catch(e) {

      debugPrint(
        "Scan Error: $e",
      );

    }

    finally {

      isScanning = false;

      if(mounted){
        setState(() {});
      }

    }

  }



  @override
  void dispose() {
    scanSubscription?.cancel();
    FlutterBluePlus.stopScan();
    super.dispose();
  }



  @override
  Widget build(BuildContext context) {

    return Scaffold(

      appBar: AppBar(

        title: const Text(
          "Scan Devices",
        ),

        centerTitle: true,

        actions: [

          IconButton(

            icon: isScanning
                ? const CircularProgressIndicator()
                : const Icon(Icons.refresh),

            onPressed: isScanning
                ? null
                : startScan,

          ),

        ],

      ),


      body: ListView.builder(

        itemCount: scanResults.length,

        itemBuilder: (context,index){

          final device =
              scanResults[index].device;


          return Card(

            margin: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 6,
            ),


            child: ListTile(

              leading: const Icon(
                Icons.bluetooth,
                color: Colors.blue,
              ),


              title: Text(

                device.platformName.isNotEmpty
                    ? device.platformName
                    : "Unknown Device",

              ),


              subtitle: Text(
                device.remoteId.toString(),
              ),


              trailing: ElevatedButton(

                child: const Text(
                  "Connect",
                ),


                onPressed: () async {

                  await FlutterBluePlus.stopScan();
                  try {

                    await bleManager.connect(device);

// Save that first-time setup is complete
                    await SetupService().completeSetup();

                    if (!mounted) return;

                    Navigator.pushReplacement(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const MyHomePage(
                          title: "TraceHer",
                        ),
                      ),
                    );


                  }

                  catch(e){

                    ScaffoldMessenger.of(context)
                        .showSnackBar(

                      SnackBar(
                        content: Text(
                          "Connection Failed\n$e",
                        ),
                      ),

                    );

                  }

                },

              ),

            ),

          );

        },

      ),

    );

  }

}