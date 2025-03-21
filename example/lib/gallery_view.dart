import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:developer';
import 'package:share_plus/share_plus.dart';

class GalleryView extends StatefulWidget {
  const GalleryView({super.key});

  @override
  State<GalleryView> createState() => _GalleryViewState();
}

class _GalleryViewState extends State<GalleryView> with SingleTickerProviderStateMixin {
  List<ImageGroup> _imageGroups = [];
  bool _isLoading = true;
  
  @override
  void initState() {
    super.initState();
    _loadImages();
  }
  
  @override
  void dispose() {
    super.dispose();
  }
  
  Future<void> _loadImages() async {
    setState(() {
      _isLoading = true;
    });
    
    try {
      final appDir = await getExternalStorageDirectory();
      final arImagesDir = Directory('${appDir!.path}/Pictures/ar_images');
      log('arImagesDir: ${arImagesDir.path}', name: 'GalleryView');
      
      if (!await arImagesDir.exists()) {
        await arImagesDir.create(recursive: true);
      }
      
      // Get all files in the directory
      final files = await arImagesDir.list().toList();
      
      // Filter for image files only and create simple groups (one file per group)
      final List<ImageGroup> groups = [];
      
      for (var fileEntity in files) {
        if (fileEntity is File) {
          final filename = fileEntity.path.split('/').last;
          final extension = filename.split('.').last.toLowerCase();
          
          // Check if it's an image file
          if (['jpg', 'jpeg', 'png'].contains(extension)) {
            // Create a timestamp from the file's modified time
            final timestamp = DateTime.now().millisecondsSinceEpoch.toString();
            final group = ImageGroup(timestamp);
            group.cameraImage = fileEntity;
            groups.add(group);
          }
        }
      }
      
      // Sort by file modification time (newest first)
      groups.sort((a, b) {
        final fileA = a.cameraImage!;
        final fileB = b.cameraImage!;
        return fileB.lastModifiedSync().compareTo(fileA.lastModifiedSync());
      });
      
      setState(() {
        _imageGroups = groups;
        _isLoading = false;
      });
    } catch (e) {
      log('Error loading images: ${e.toString()}', name: 'GalleryView');
      setState(() {
        _isLoading = false;
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Saved AR Images'),
        actions: [
          if (_imageGroups.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_forever),
              tooltip: 'Delete All Images',
              onPressed: _showDeleteAllConfirmation,
            ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadImages,
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _imageGroups.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.photo_library_outlined, size: 64, color: Colors.grey),
                      const SizedBox(height: 16),
                      const Text('No images saved yet', style: TextStyle(fontSize: 16)),
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('Return to Camera'),
                      ),
                    ],
                  ),
                )
              : _buildImageGrid(),
    );
  }
  
  Widget _buildImageGrid() {
    final filteredGroups = _imageGroups
        .where((group) => group.cameraImage != null)
        .toList();
    
    if (filteredGroups.isEmpty) {
      return const Center(
        child: Text('No images available'),
      );
    }
    
    return RefreshIndicator(
      onRefresh: _loadImages,
      child: GridView.builder(
        padding: const EdgeInsets.all(8),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          crossAxisSpacing: 8,
          mainAxisSpacing: 8,
          childAspectRatio: 1,
        ),
        itemCount: filteredGroups.length,
        itemBuilder: (context, index) {
          final file = filteredGroups[index].cameraImage;
          if (file == null) return const SizedBox.shrink();
          
          return InkWell(
            onTap: () => _showImageDetail(file),
            child: Card(
              clipBehavior: Clip.antiAlias,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.file(
                    file,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      return const Center(
                        child: Icon(Icons.broken_image, size: 48, color: Colors.red),
                      );
                    },
                  ),
                  Positioned(
                    bottom: 0,
                    left: 0,
                    right: 0,
                    child: Container(
                      color: Colors.black54,
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Text(
                        _formatTimestamp(filteredGroups[index].timestamp),
                        style: const TextStyle(color: Colors.white),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
  
  void _showImageDetail(File file) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => Scaffold(
          appBar: AppBar(
            title: Text(file.path.split('/').last),
            actions: [
              IconButton(
                icon: const Icon(Icons.share),
                onPressed: () => _shareImage(file),
                tooltip: 'Share',
              ),
              IconButton(
                icon: const Icon(Icons.delete),
                onPressed: () {
                  _deleteImage(file);
                  Navigator.pop(context);
                },
                tooltip: 'Delete',
              ),
            ],
          ),
          body: Center(
            child: InteractiveViewer(
              boundaryMargin: const EdgeInsets.all(20),
              minScale: 0.5,
              maxScale: 4.0,
              child: Image.file(
                file,
                fit: BoxFit.contain,
                errorBuilder: (context, error, stackTrace) {
                  return const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.broken_image, size: 64, color: Colors.red),
                        SizedBox(height: 16),
                        Text('Failed to load image'),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }
  
  Future<void> _deleteImage(File file) async {
    try {
      await file.delete();
      _loadImages(); // Refresh the grid
    } catch (e) {
      log('Error deleting image: ${e.toString()}', name: 'GalleryView');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to delete image: ${e.toString()}')),
        );
      }
    }
  }
  
  Future<void> _shareImage(File file) async {
    try {
      await Share.shareXFiles([XFile(file.path)], text: 'AR Depth Image');
    } catch (e) {
      log('Error sharing image: ${e.toString()}', name: 'GalleryView');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to share image: ${e.toString()}')),
        );
      }
    }
  }
  
  void _showDeleteAllConfirmation() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete All Images'),
        content: const Text(
          'Are you sure you want to delete all saved images? This action cannot be undone.'
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('CANCEL'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              _deleteAllImages();
            },
            child: const Text('DELETE ALL', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
  
  Future<void> _deleteAllImages() async {
    try {
      setState(() {
        _isLoading = true;
      });
      
      final appDir = await getApplicationDocumentsDirectory();
      final arImagesDir = Directory('${appDir.path}/ar_images');
      
      if (await arImagesDir.exists()) {
        // Get all files in the directory
        final files = await arImagesDir.list().toList();
        
        // Delete each file
        for (var entity in files) {
          if (entity is File) {
            await entity.delete();
          }
        }
      }
      
      setState(() {
        _imageGroups = [];
        _isLoading = false;
      });
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('All images deleted successfully'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      log('Error deleting all images: ${e.toString()}', name: 'GalleryView');
      setState(() {
        _isLoading = false;
      });
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to delete all images: ${e.toString()}'),
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }
  
  String _formatTimestamp(String timestamp) {
    try {
      final ms = int.parse(timestamp);
      final dateTime = DateTime.fromMillisecondsSinceEpoch(ms);
      return '${dateTime.month}/${dateTime.day} ${dateTime.hour}:${dateTime.minute.toString().padLeft(2, '0')}';
    } catch (e) {
      return timestamp;
    }
  }
}

class ImageGroup {
  final String timestamp;
  File? depthImage;
  File? confidenceImage;
  File? cameraImage;
  
  ImageGroup(this.timestamp);
} 