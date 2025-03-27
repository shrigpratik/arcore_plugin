import 'dart:ui' as ui;
import 'package:flutter/material.dart';

class DepthHeatmapVisualizer extends StatefulWidget {
  final List<double> depthData;
  final int width;
  final int height;
  final double minDepthThreshold;
  final double maxDepthThreshold;

  const DepthHeatmapVisualizer({
    super.key,
    required this.depthData,
    required this.width,
    required this.height,
    this.minDepthThreshold = 0.0,
    this.maxDepthThreshold = 3.0, // Default max depth of 3 meters
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
      Colors.lightGreen, // Shallow depths (0-10m)
      Colors.green,
      Colors.teal, // Mid depths (10-25m)
      Colors.blue,
      Colors.blue[900], // Deeper depths (25-40m)
      Colors.indigo,
      Colors.purple, // Very deep (40-50m)
      Colors.deepPurple,
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
            quarterTurns: 3,
            child: RawImage(image: _heatmapImage, fit: BoxFit.contain),
          ),
        )
        : const CircularProgressIndicator();
  }
}
