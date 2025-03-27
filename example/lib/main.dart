import 'dart:developer';

import 'package:ar_depth_cover/ar_view.dart';
import 'package:ar_depth_cover_example/depthmap_visualization.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:ar_depth_cover/ar_depth_cover.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';
// import 'gallery_view.dart'; // Import the gallery view
import 'dart:convert';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AR Depth Cover Demo',
      theme: ThemeData(primarySwatch: Colors.blue, useMaterial3: true),
      home: const ARHomePage(),
    );
  }
}

class ARHomePage extends StatefulWidget {
  const ARHomePage({super.key});

  @override
  State<ARHomePage> createState() => _ARHomePageState();
}

class _ARHomePageState extends State<ARHomePage> {
  String _platformVersion = 'Unknown';
  final _arDepthCoverPlugin = ArDepthCover();

  // Depth data information
  Map<String, dynamic>? _depthData;
  String _depthInfoText = 'No depth data received yet';

  // Add this variable to your _ARHomePageState class
  // int _lastSaveTimestamp = 0;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;

    // Request camera permission
    var cameraStatus = await Permission.camera.request();
    if (cameraStatus.isDenied) {
      // Handle permission denied scenario
      platformVersion = 'Camera permission denied';
      setState(() {
        _platformVersion = platformVersion;
      });
      return;
    }

    // Request storage permission
    var storageStatus = await Permission.photos.request();
    if (storageStatus.isDenied) {
      log('Storage permission denied', name: 'STORAGE PERMISSION');
      storageStatus = await Permission.storage.request();
    }

    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion =
          await _arDepthCoverPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  // Handle depth data received from AR view
  void _onDepthDataReceived(Map<String, dynamic> depthData) {
    if (!mounted) return;

    setState(() {
      _depthData = depthData;
      log('depthData: ${depthData.keys}', name: 'Received Depth Data');
      // log(depthData.toString(), name: 'Received Depth Data');
      // Format text with relevant depth information
      _depthInfoText =
          'Depth Info:\n'
          'Intrinsics: ${depthData['intrinsicsWidth']}x${depthData['intrinsicsHeight']}\n'
          'Depth Image: ${depthData['depthWidth']}x${depthData['depthHeight']}\n'
          'Focal Length: (${depthData['focalLengthX']?.toStringAsFixed(2)}, ${depthData['focalLengthY']?.toStringAsFixed(2)})\n'
          'Principal Point: (${depthData['principalPointX']?.toStringAsFixed(2)}, ${depthData['principalPointY']?.toStringAsFixed(2)})\n'
          'Sample Size: ${depthData['sampleWidth']}x${depthData['sampleHeight']}';
      log('imagePath: ${depthData['imagePath']}', name: 'Received Depth Data');
      log(
        'depthData: ${depthData['depthImage'].length}',
        name: 'Received Depth Data',
      );
      log(
        'confidenceData: ${depthData['confidenceImage']['planes'][0]['data'].length}',
        name: 'Received Depth Data',
      );

      // Save depth and confidence data to a JSON file
      _saveDepthAndConfidenceData(depthData);
    });
  }

  // Function to save depth and confidence data
  Future<void> _saveDepthAndConfidenceData(
    Map<String, dynamic> depthData,
  ) async {
    try {
      // Get the image path and extract a filename base
      String? imagePath = depthData['imagePath'];
      if (imagePath == null) {
        log('Cannot save data: Image path is null', name: 'Save Data Error');
        return;
      }
      log(imagePath, name: "Data Saved");
      // Create a filename based on the image path
      String baseFilename = imagePath.split('/').last.split('.').first;
      // Get the directory path by removing the last component (filename)
      String directoryPath = imagePath
          .split('/')
          .sublist(0, imagePath.split('/').length - 1)
          .join('/');
      String jsonFilename = '${baseFilename}_depth_data.json';

      // Get app's documents directory

      log('directory: $directoryPath', name: 'Data Saved');
      final filePath = '$directoryPath/$jsonFilename';

      // Prepare data to save
      Map<String, dynamic> dataToSave = {
        'timestamp': DateTime.now().millisecondsSinceEpoch,
        'depthWidth': depthData['depthWidth'],
        'depthHeight': depthData['depthHeight'],
        'intrinsicsWidth': depthData['intrinsicsWidth'],
        'intrinsicsHeight': depthData['intrinsicsHeight'],
        'focalLengthX': depthData['focalLengthX'],
        'focalLengthY': depthData['focalLengthY'],
        'principalPointX': depthData['principalPointX'],
        'principalPointY': depthData['principalPointY'],
        'modelMatrix': depthData['modelMatrix'],
        'viewMatrix': depthData['viewMatrix'],
        'projectionMatrix': depthData['projectionMatrix'],
        'transformMatrix': depthData['transformMatrix'],
        'depthData': List<double>.from(depthData['depthImage']),
        'confidenceData': List<int>.from(
          depthData['confidenceImage']['planes'][0]['data'],
        ),
        'originalImagePath': imagePath,
      };

      // Convert to JSON and save to file
      final jsonString = jsonEncode(dataToSave);
      final file = File(filePath);
      await file.writeAsString(jsonString);

      log(
        'Successfully saved depth and confidence data to $filePath',
        name: 'Data Saved',
      );
    } catch (e) {
      log(
        'Error saving depth and confidence data: $e',
        name: 'Save Data Error',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AR Depth Cover Example'),
        actions: [
          // Add a gallery button to the app bar
          IconButton(
            icon: const Icon(Icons.photo_library),
            tooltip: 'View Saved Images',
            onPressed: () {
              if (_depthData == null) return;
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder:
                      (context) => DepthHeatmapVisualizer(
                        depthData: List<double>.from(_depthData!['depthImage']),
                        height: _depthData!['depthHeight'],
                        width: _depthData!['depthWidth'],
                      ),
                ),
              );
              // Navigator.push(
              //   context,
              //   MaterialPageRoute(builder: (context) => GalleryView()),
              // );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text(
              'Status: $_platformVersion',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
          // Display depth data information
          Container(
            padding: const EdgeInsets.all(8.0),
            color: Colors.black12,
            width: double.infinity,
            child: Text(
              _depthInfoText,
              style: const TextStyle(fontFamily: 'monospace'),
            ),
          ),
          // AR View takes most of the screen
          Expanded(child: _renderARView()),
          // Add an informational text at the bottom
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              'Images are automatically saved. Tap the gallery icon to view saved images.',
              style: TextStyle(
                fontStyle: FontStyle.italic,
                color: Colors.grey[700],
              ),
              textAlign: TextAlign.center,
            ),
          ),
        ],
      ),
    );
  }

  Widget _renderARView() {
    try {
      return ARView(
        logDepthOnly: false, // Set to false to receive depth data visualization
        onDepthDataReceived: _onDepthDataReceived,
      );
    } catch (e) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 48, color: Colors.red),
            const SizedBox(height: 16),
            Text('Could not initialize AR: ${e.toString()}'),
            const SizedBox(height: 16),
            Text(
              'This device may not support the required AR capabilities.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }
  }
}
