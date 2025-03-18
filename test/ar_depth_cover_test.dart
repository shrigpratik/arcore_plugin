import 'package:flutter_test/flutter_test.dart';
import 'package:ar_depth_cover/ar_depth_cover.dart';
import 'package:ar_depth_cover/ar_depth_cover_platform_interface.dart';
import 'package:ar_depth_cover/ar_depth_cover_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockArDepthCoverPlatform
    with MockPlatformInterfaceMixin
    implements ArDepthCoverPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ArDepthCoverPlatform initialPlatform = ArDepthCoverPlatform.instance;

  test('$MethodChannelArDepthCover is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelArDepthCover>());
  });

  test('getPlatformVersion', () async {
    ArDepthCover arDepthCoverPlugin = ArDepthCover();
    MockArDepthCoverPlatform fakePlatform = MockArDepthCoverPlatform();
    ArDepthCoverPlatform.instance = fakePlatform;

    expect(await arDepthCoverPlugin.getPlatformVersion(), '42');
  });
}
