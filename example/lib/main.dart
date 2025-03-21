import 'dart:developer';

import 'package:ar_depth_cover/ar_view.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:ar_depth_cover/ar_depth_cover.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import 'dart:typed_data';
import 'gallery_view.dart'; // Import the gallery view

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AR Depth Cover Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
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
  int _lastSaveTimestamp = 0;

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
          await _arDepthCoverPlugin.getPlatformVersion() ?? 'Unknown platform version';
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
      _depthInfoText = 'Depth Info:\n'
          'Intrinsics: ${depthData['intrinsicsWidth']}x${depthData['intrinsicsHeight']}\n'
          'Depth Image: ${depthData['depthWidth']}x${depthData['depthHeight']}\n'
          'Focal Length: (${depthData['focalLengthX']?.toStringAsFixed(2)}, ${depthData['focalLengthY']?.toStringAsFixed(2)})\n'
          'Principal Point: (${depthData['principalPointX']?.toStringAsFixed(2)}, ${depthData['principalPointY']?.toStringAsFixed(2)})\n'
          'Sample Size: ${depthData['sampleWidth']}x${depthData['sampleHeight']}';
          log('imagePath: ${depthData['imagePath']}', name: 'Received Depth Data');
          log('depthData: ${depthData['depthImage'].length }', name: 'Received Depth Data');
          log('confidenceData: ${depthData['confidenceImage']['planes'][0]['data'].length}', name: 'Received Depth Data');
    });

    
    // Only save images every 2 seconds
    // final currentTime = DateTime.now().millisecondsSinceEpoch;
    // if (currentTime - _lastSaveTimestamp > 200) {  // 2000 ms = 2 seconds
    //   _lastSaveTimestamp = currentTime;
    //   _saveARImages(depthData);
    // }
  }
  
  // Method to save AR images from depth data
  Future<void> _saveARImages(Map<String, dynamic> depthData) async {
    try {
      // Check storage permission before attempting to save
      var storageStatus = await Permission.photos.status;
      if (!storageStatus.isGranted) {
        // storageStatus = await Permission.storage.request();
        if (!storageStatus.isGranted) {
          log('Storage permission not granted', name: 'ImageSaver');
          return;
        }
      }
      
      // Extract images from depth data and convert to Uint8List
      final depthImage = _convertToUint8List(depthData['depthImage']);
      final confidenceImage = _convertToUint8List(depthData['confidenceImage']);
      final cameraImage = _convertToUint8List(depthData['cameraImage']);
      
      // Save the images
      final savedPaths = await _saveImagesToStorage(
        depthImage: depthImage,
        confidenceImage: confidenceImage,
        cameraImage: cameraImage,
      );
      
      if (savedPaths.isNotEmpty) {
        log('Saved images: ${savedPaths.join(", ")}', name: 'ImageSaver');
        if (mounted) {
          // Show a more prominent notification with action button
          WidgetsBinding.instance.addPostFrameCallback((_) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('${savedPaths.length} images saved'),
                duration: const Duration(seconds: 3),
                action: SnackBarAction(
                  label: 'VIEW',
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (context) => const GalleryView()),
                    );
                  },
                ),
              ),
            );
          });
        }
      }
    } catch (e) {
      log('Error saving images: ${e.toString()}', name: 'ImageSaver');
    }
  }
  
  // Helper method to convert dynamic data to Uint8List
  Uint8List? _convertToUint8List(dynamic data) {
    if (data == null) return null;
    
    if (data is Uint8List) return data;
    
    if (data is List) {
      return Uint8List.fromList(data.map((e) => e as int).toList());
    }
    
    if (data is Map) {
      // Handle the format created by addRawImageDataToMap in Android
      if (data.containsKey('planes') && data['planes'] is List) {
        List planes = data['planes'] as List;
        if (planes.isNotEmpty) {
          // Fix the type cast issue here
          Map<Object?, Object?> planeData = planes.first as Map<Object?, Object?>;
          if (planeData.containsKey('data')) {
            var rawBytes = planeData['data'];
            if (rawBytes is List) {
              log('Plane bytes: ${rawBytes.length}', name: 'ImageSaver');
              // Safely cast the list elements to int
              return Uint8List.fromList(rawBytes.map((e) => e as int).toList());
            }
          }
        }
      }
      
      // Original handling for other map formats
      if (data.containsKey('bytes')) {
        var bytes = data['bytes'];
        if (bytes is List) {
          log('bytes: ${bytes.length}', name: 'ImageSaver');
          return Uint8List.fromList(bytes.map((e) => e as int).toList());
        }
      }
      
      // If 'data' directly contains the bytes (simpler format)
      if (data.containsKey('data')) {
        var directData = data['data'];
        if (directData is List) {
          log('direct data: ${directData.length}', name: 'ImageSaver');
          return Uint8List.fromList(directData.map((e) => e as int).toList());
        }
      }
    }
    
    return null;
  }
  
  // Helper method to save images to app's documents directory
  Future<List<String>> _saveImagesToStorage({
    Uint8List? depthImage,
    Uint8List? confidenceImage,
    Uint8List? cameraImage,
  }) async {
    final List<String> savedPaths = [];
    final timestamp = DateTime.now().millisecondsSinceEpoch.toString();
    
    // Get the application documents directory
    final appDir = await getApplicationDocumentsDirectory();
    final arImagesDir = Directory('${appDir.path}/ar_images');
     log('arImagesDir: ${arImagesDir.path}', name: 'ImageSaver');
    // Create the directory if it doesn't exist
    if (!await arImagesDir.exists()) {
      await arImagesDir.create(recursive: true);
    }
    log('deppthImage: ${depthImage?.length}', name: 'ImageSaver');
    // Save depth image if available
    if (depthImage != null && depthImage.isNotEmpty) {
      final filePath = '${arImagesDir.path}/depth_$timestamp.png';
      print('Saving depth image to $filePath');
      final file = File(filePath);
      await file.writeAsBytes(depthImage);
      savedPaths.add('Depth image');
      log('Saved depth image to $filePath', name: 'ImageSaver');
    }
    
    // Save confidence image if available
    if (confidenceImage != null && confidenceImage.isNotEmpty) {
      final filePath = '${arImagesDir.path}/confidence_$timestamp.png';
      print('Saving confidence image to $filePath');
      final file = File(filePath);
      await file.writeAsBytes(confidenceImage);
      savedPaths.add('Confidence image');
      log('Saved confidence image to $filePath', name: 'ImageSaver');
    }
    
    // Save camera image if available
    if (cameraImage != null && cameraImage.isNotEmpty) {
      final filePath = '${arImagesDir.path}/camera_$timestamp.jpg';
      print('Saving camera image to $filePath');
      final file = File(filePath);
      await file.writeAsBytes(cameraImage);
      savedPaths.add('Camera image');
      log('Saved camera image to $filePath', name: 'ImageSaver');
    }
    
    return savedPaths;
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
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const GalleryView()),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text('Status: $_platformVersion', 
              style: const TextStyle(fontWeight: FontWeight.bold)),
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
          Expanded(
            child: _renderARView(),
          ),
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
            Text('This device may not support the required AR capabilities.', 
              textAlign: TextAlign.center)
          ],
        ),
      );
    }
  }
}
