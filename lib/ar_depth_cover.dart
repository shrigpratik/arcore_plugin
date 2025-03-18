
import 'ar_depth_cover_platform_interface.dart';

class ArDepthCover {
  Future<String?> getPlatformVersion() {
    return ArDepthCoverPlatform.instance.getPlatformVersion();
  }
}
