// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'models.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

NumericStruct _$NumericStructFromJson(Map<String, dynamic> json) =>
    NumericStruct(
      f1: (json['f1'] as num).toInt(),
      f2: (json['f2'] as num).toInt(),
      f3: (json['f3'] as num).toInt(),
      f4: (json['f4'] as num).toInt(),
      f5: (json['f5'] as num).toInt(),
      f6: (json['f6'] as num).toInt(),
      f7: (json['f7'] as num).toInt(),
      f8: (json['f8'] as num).toInt(),
    );

Map<String, dynamic> _$NumericStructToJson(NumericStruct instance) =>
    <String, dynamic>{
      'f1': instance.f1,
      'f2': instance.f2,
      'f3': instance.f3,
      'f4': instance.f4,
      'f5': instance.f5,
      'f6': instance.f6,
      'f7': instance.f7,
      'f8': instance.f8,
    };

Sample _$SampleFromJson(Map<String, dynamic> json) => Sample(
      intValue: (json['intValue'] as num).toInt(),
      longValue: (json['longValue'] as num).toInt(),
      floatValue: _float32FromJson(json['floatValue'] as num),
      doubleValue: (json['doubleValue'] as num).toDouble(),
      shortValue: (json['shortValue'] as num).toInt(),
      charValue: (json['charValue'] as num).toInt(),
      booleanValue: json['booleanValue'] as bool,
      intValueBoxed: (json['intValueBoxed'] as num).toInt(),
      longValueBoxed: (json['longValueBoxed'] as num).toInt(),
      floatValueBoxed: _float32FromJson(json['floatValueBoxed'] as num),
      doubleValueBoxed: (json['doubleValueBoxed'] as num).toDouble(),
      shortValueBoxed: (json['shortValueBoxed'] as num).toInt(),
      charValueBoxed: (json['charValueBoxed'] as num).toInt(),
      booleanValueBoxed: json['booleanValueBoxed'] as bool,
      intArray: _int32ListFromJson(json['intArray'] as List),
      longArray: _int64ListFromJson(json['longArray'] as List),
      floatArray: _float32ListFromJson(json['floatArray'] as List),
      doubleArray: _float64ListFromJson(json['doubleArray'] as List),
      shortArray: _int32ListFromJson(json['shortArray'] as List),
      charArray: _int32ListFromJson(json['charArray'] as List),
      booleanArray: _boolListFromJson(json['booleanArray'] as List),
      string: json['string'] as String,
    );

Map<String, dynamic> _$SampleToJson(Sample instance) => <String, dynamic>{
      'intValue': instance.intValue,
      'longValue': instance.longValue,
      'floatValue': _float32ToJson(instance.floatValue),
      'doubleValue': instance.doubleValue,
      'shortValue': instance.shortValue,
      'charValue': instance.charValue,
      'booleanValue': instance.booleanValue,
      'intValueBoxed': instance.intValueBoxed,
      'longValueBoxed': instance.longValueBoxed,
      'floatValueBoxed': _float32ToJson(instance.floatValueBoxed),
      'doubleValueBoxed': instance.doubleValueBoxed,
      'shortValueBoxed': instance.shortValueBoxed,
      'charValueBoxed': instance.charValueBoxed,
      'booleanValueBoxed': instance.booleanValueBoxed,
      'intArray': _int32ListToJson(instance.intArray),
      'longArray': _int64ListToJson(instance.longArray),
      'floatArray': _float32ListToJson(instance.floatArray),
      'doubleArray': _float64ListToJson(instance.doubleArray),
      'shortArray': _int32ListToJson(instance.shortArray),
      'charArray': _int32ListToJson(instance.charArray),
      'booleanArray': _boolListToJson(instance.booleanArray),
      'string': instance.string,
    };

Media _$MediaFromJson(Map<String, dynamic> json) => Media(
      uri: json['uri'] as String,
      title: json['title'] as String,
      width: (json['width'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      format: json['format'] as String,
      duration: (json['duration'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      bitrate: (json['bitrate'] as num).toInt(),
      hasBitrate: json['hasBitrate'] as bool,
      persons:
          (json['persons'] as List<dynamic>).map((e) => e as String).toList(),
      player: $enumDecode(_$PlayerEnumMap, json['player']),
      copyright: json['copyright'] as String,
    );

Map<String, dynamic> _$MediaToJson(Media instance) => <String, dynamic>{
      'uri': instance.uri,
      'title': instance.title,
      'width': instance.width,
      'height': instance.height,
      'format': instance.format,
      'duration': instance.duration,
      'size': instance.size,
      'bitrate': instance.bitrate,
      'hasBitrate': instance.hasBitrate,
      'persons': instance.persons,
      'player': _$PlayerEnumMap[instance.player]!,
      'copyright': instance.copyright,
    };

const _$PlayerEnumMap = {
  Player.java: 'java',
  Player.flash: 'flash',
};

Image _$ImageFromJson(Map<String, dynamic> json) => Image(
      uri: json['uri'] as String,
      title: json['title'] as String,
      width: (json['width'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      size: $enumDecode(_$MediaSizeEnumMap, json['size']),
    );

Map<String, dynamic> _$ImageToJson(Image instance) => <String, dynamic>{
      'uri': instance.uri,
      'title': instance.title,
      'width': instance.width,
      'height': instance.height,
      'size': _$MediaSizeEnumMap[instance.size]!,
    };

const _$MediaSizeEnumMap = {
  MediaSize.small: 'small',
  MediaSize.large: 'large',
};

MediaContent _$MediaContentFromJson(Map<String, dynamic> json) => MediaContent(
      media: Media.fromJson(json['media'] as Map<String, dynamic>),
      images: (json['images'] as List<dynamic>)
          .map((e) => Image.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$MediaContentToJson(MediaContent instance) =>
    <String, dynamic>{
      'media': instance.media,
      'images': instance.images,
    };

StructList _$StructListFromJson(Map<String, dynamic> json) => StructList(
      structList: (json['structList'] as List<dynamic>)
          .map((e) => NumericStruct.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$StructListToJson(StructList instance) =>
    <String, dynamic>{
      'structList': instance.structList,
    };

SampleList _$SampleListFromJson(Map<String, dynamic> json) => SampleList(
      sampleList: (json['sampleList'] as List<dynamic>)
          .map((e) => Sample.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$SampleListToJson(SampleList instance) =>
    <String, dynamic>{
      'sampleList': instance.sampleList,
    };

MediaContentList _$MediaContentListFromJson(Map<String, dynamic> json) =>
    MediaContentList(
      mediaContentList: (json['mediaContentList'] as List<dynamic>)
          .map((e) => MediaContent.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$MediaContentListToJson(MediaContentList instance) =>
    <String, dynamic>{
      'mediaContentList': instance.mediaContentList,
    };
