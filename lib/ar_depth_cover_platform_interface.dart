import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'ar_depth_cover_method_channel.dart';

abstract class ArDepthCoverPlatform extends PlatformInterface {
  /// Constructs a ArDepthCoverPlatform.
  ArDepthCoverPlatform() : super(token: _token);

  static final Object _token = Object();

  static ArDepthCoverPlatform _instance = MethodChannelArDepthCover();

  /// The default instance of [ArDepthCoverPlatform] to use.
  ///
  /// Defaults to [MethodChannelArDepthCover].
  static ArDepthCoverPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ArDepthCoverPlatform] when
  /// they register themselves.
  static set instance(ArDepthCoverPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
