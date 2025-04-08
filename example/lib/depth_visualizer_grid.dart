import 'package:ar_depth_cover_example/depthmap_visualization.dart';
import 'package:flutter/material.dart';

class DepthVisualizerGrid extends StatelessWidget {
  const DepthVisualizerGrid({super.key, required this.depthImageList});
  final List<DepthImage> depthImageList;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: GridView.builder(
        primary: false,
        padding: const EdgeInsets.all(12),
        itemCount: depthImageList.length,
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 3,
        ),
        itemBuilder:
            (context, index) => GestureDetector(
              onTap:
                  () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder:
                          (context) => DepthHeatmapVisualizer(
                            depthData: depthImageList[index].depthData,
                            width: depthImageList[index].width,
                            height: depthImageList[index].height,
                            imagePath: depthImageList[index].imagePath,
                          ),
                    ),
                  ),
              child: Container(
                height: 200,
                padding: EdgeInsets.all(10),
                child: DepthHeatmapVisualizer(
                  depthData: depthImageList[index].depthData,
                  width: depthImageList[index].width,
                  height: depthImageList[index].height,
                  imagePath: depthImageList[index].imagePath,
                ),
              ),
            ),
      ),
    );
  }
}

class DepthImage {
  final List<double> depthData;
  final int width;
  final int height;
  final String imagePath;

  DepthImage({
    required this.depthData,
    required this.width,
    required this.height,
    required this.imagePath,
  });

  @override
  String toString() {
    return 'DepthImage(width: $width, height: $height, imagePath: $imagePath, depthData length: ${depthData.length})';
  }
}
