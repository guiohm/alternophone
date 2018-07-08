import 'package:alternophone/audio/audioplayer.dart';
import 'package:flutter/material.dart';

import 'package:intl/intl.dart';

class MediaPlayerWidget extends StatefulWidget {
  @override
  _MediaPlayerState createState() => new _MediaPlayerState();
}

class _MediaPlayerState extends State<MediaPlayerWidget> {

  static const Icon _pauseIcon = const Icon(Icons.pause);
  static const Icon _playIcon = const Icon(Icons.play_arrow);
  static const Icon _stopIcon = const Icon(Icons.stop);

  // Used to format duration.
  static NumberFormat _twoDigits = new NumberFormat('00', 'en_GB');

  Audioplayer _audioplayer = new Audioplayer();

  /// Returns the duration as a formatted string.
  String _formatDuration(Duration duration) {
    return '${_twoDigits.format(duration.inSeconds ~/ 60)}:${_twoDigits
        .format(duration.inSeconds % 60)}';
  }

  /// Returns the slider value.
  double _getSliderValue() {
    int position = _audioplayer.position.inSeconds;
    if (position <= 0) {
      return 0.0;
    } else if (position >= _audioplayer.duration.inSeconds) {
      return _audioplayer.duration.inSeconds.toDouble();
    } else {
      return position.toDouble();
    }
  }

  @override
  void initState() {
    super.initState();

    _audioplayer.durationNotifier.addListener(() => setState(() {}));
    _audioplayer.isPlayingNotifier.addListener(() => setState(() {}));
    _audioplayer.positionNotifier.addListener(() => setState(() {}));

    _audioplayer.completionHandler = () => _audioplayer.stop();
  }

  @override
  Widget build(BuildContext context) {
    return new Column(children: <Widget>[
      new Wrap(
        alignment: WrapAlignment.spaceAround,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 12.0,
        runSpacing: 8.0,
        children: <Widget>[
          new IconButton(
              icon: _playIcon, iconSize: 30.0, onPressed: () => _audioplayer.play()),
          new IconButton(
              icon: _pauseIcon,
              iconSize: 30.0,
              onPressed: () => _audioplayer.pause()),
          new IconButton(
              icon: _stopIcon, iconSize: 30.0, onPressed: () => _audioplayer.stop()),
          // This button is disabled since it's only there to show the current
          // state of the player.
          new IconButton(
              icon: _audioplayer.isPlaying ? _pauseIcon : _playIcon,
              iconSize: 50.0,
              onPressed: null)
        ],
      ),
      new Row(mainAxisSize: MainAxisSize.max, children: <Widget>[
        new Container(
            width: 50.0,
            child: new Text(_formatDuration(_audioplayer.position),
                textAlign: TextAlign.left)),
        new Expanded(
            child: new Slider(
                value: _getSliderValue(),
                max: _audioplayer.duration.inSeconds.toDouble(),
                onChanged: (double newValue) =>
                    _audioplayer.seek(new Duration(seconds: newValue.ceil())))),
        new Container(
            width: 50.0,
            child: new Text('-' + _formatDuration(_audioplayer.remaining),
                textAlign: TextAlign.right))
      ])
    ]);
  }
}