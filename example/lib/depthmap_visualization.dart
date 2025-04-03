import 'dart:convert';
import 'dart:developer';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';

class DepthHeatmapVisualizer extends StatefulWidget {
  final List<double> depthData;
  final int width;
  final int height;
  final String imagePath;
  final double minDepthThreshold;
  final double maxDepthThreshold;
  final Map<String, dynamic> dataToSave;

  const DepthHeatmapVisualizer({
    super.key,
    required this.depthData,
    required this.width,
    required this.height,
    required this.imagePath,
    this.minDepthThreshold = 0.5,
    this.maxDepthThreshold = 3.0,
    required this.dataToSave, // Default depth threshold in meters
  });

  @override
  State<DepthHeatmapVisualizer> createState() => _DepthHeatmapVisualizerState();
}

class _DepthHeatmapVisualizerState extends State<DepthHeatmapVisualizer> {
  ui.Image? _heatmapImage;

  @override
  void initState() {
    super.initState();
    _generateHeatmap();
    saveDepthData();
  }

  Future<void> saveDepthData() async {
    try {
      // Validate image path
      if (widget.imagePath.isEmpty) {
        log('Cannot save data: Image path is empty', name: 'Save Data Error');
        return;
      }

      log(widget.imagePath, name: "Data Saved");

      // Create a filename based on the image path
      String baseFilename = widget.imagePath.split('/').last.split('.').first;

      // Get the directory path by removing the last component (filename)
      String directoryPath = widget.imagePath
          .split('/')
          .sublist(0, widget.imagePath.split('/').length - 1)
          .join('/');

      String jsonFilename = '${baseFilename}_depth_data.json';
      final filePath = '$directoryPath/$jsonFilename';

      // Prepare simplified data to save - only width, height and depth data

      // Convert to JSON and save to file
      final jsonString = jsonEncode(widget.dataToSave);
      final file = File(filePath);
      await file.writeAsString(jsonString);

      log('Successfully saved depth data to $filePath', name: 'Data Saved');
    } catch (e) {
      log('Error saving depth data: $e', name: 'Save Data Error');
    }
  }

  void _generateHeatmap() async {
    // Clip depth values to the specified threshold range
    final clippedDepthData =
        widget.depthData
            .map(
              (depth) => depth.clamp(
                widget.minDepthThreshold,
                widget.maxDepthThreshold,
              ),
            )
            .toList();

    final minDepth = widget.minDepthThreshold;
    final maxDepth = widget.maxDepthThreshold;

    // Create a UI Image with depth data
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    // Create a paint object for coloring
    final paint = Paint();

    // Iterate through depth data and color pixels
    for (int y = 0; y < widget.height; y++) {
      for (int x = 0; x < widget.width; x++) {
        final index = y * widget.width + x;
        final depth = clippedDepthData[index];

        // Normalize depth value between 0 and 1
        final normalizedDepth = (depth - minDepth) / (maxDepth - minDepth);

        // Create color gradient based on depth
        final color = _getColorForDepth(normalizedDepth);

        paint.color = color;
        canvas.drawRect(
          Rect.fromPoints(
            Offset(x.toDouble(), y.toDouble()),
            Offset(x.toDouble() + 1, y.toDouble() + 1),
          ),
          paint,
        );
      }
    }

    // Convert canvas to image
    final picture = recorder.endRecording();
    final image = await picture.toImage(widget.width, widget.height);

    // Update state with heatmap image
    setState(() {
      _heatmapImage = image;
    });
  }

  Color _getColorForDepth(double normalizedDepth) {
    // More nuanced color gradient for meter-based depth
    final gradient = [
      Colors.red, // Nearest depth
      Colors.orange,
      Colors.yellow,
      Colors.green,
      Colors.cyan,
      Colors.blue, // Mid depths
      Colors.indigo,
      Colors.purple,
      Colors.deepPurple, // Farthest depth
    ];

    // Determine which pair of colors to interpolate between
    final index = (normalizedDepth * (gradient.length - 1)).floor();
    final nextIndex = (index + 1).clamp(0, gradient.length - 1);

    // Calculate local interpolation within this color segment
    final localDepth = (normalizedDepth * (gradient.length - 1)) - index;

    return Color.lerp(gradient[index], gradient[nextIndex], localDepth) ??
        Colors.black;
  }

  @override
  Widget build(BuildContext context) {
    return _heatmapImage != null
        ? Material(
          child: RotatedBox(
            quarterTurns: 1,
            child: RawImage(image: _heatmapImage, fit: BoxFit.contain),
          ),
        )
        : const CircularProgressIndicator();
  }
}
