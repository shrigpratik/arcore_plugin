import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Callback type for receiving depth data from the AR camera
typedef DepthDataCallback = void Function(Map<String, dynamic> depthData);

class ARView extends StatefulWidget {
  final DepthDataCallback? onDepthDataReceived;
  final bool logDepthOnly;

  const ARView({
    Key? key,
    this.onDepthDataReceived,
    this.logDepthOnly = true,
  }) : super(key: key);

  @override
  State<ARView> createState() => _ARViewState();
}

class _ARViewState extends State<ARView> {
  late MethodChannel _channel;

  @override
  void initState() {
    super.initState();
    // The channel name must match the one used in the native code
    _channel = const MethodChannel('ar_depth_cover/depth_data');
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  // Handle incoming method calls from native code
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onDepthDataReceived':
        if (widget.onDepthDataReceived != null) {
          final Map<String, dynamic> depthData = 
              Map<String, dynamic>.from(call.arguments);
          widget.onDepthDataReceived!(depthData);
        }
        break;
      default:
        print('Unknown method ${call.method}');
    }
  }

  @override
  Widget build(BuildContext context) {
    // This is used in the platform side to register the view.
    const String viewType = 'ar_depth_view';
    // Pass parameters to the platform side.
    final Map<String, dynamic> creationParams = <String, dynamic>{
      'logDepthOnly': widget.logDepthOnly,
    };

    return AndroidView(
      viewType: viewType,
      layoutDirection: TextDirection.ltr,
      creationParams: creationParams,
      creationParamsCodec: const StandardMessageCodec(),
    );
  }
}
