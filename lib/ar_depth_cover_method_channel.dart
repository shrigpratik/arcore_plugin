import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'ar_depth_cover_platform_interface.dart';

/// An implementation of [ArDepthCoverPlatform] that uses method channels.
class MethodChannelArDepthCover extends ArDepthCoverPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('ar_depth_cover');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
