// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package benchmark

import (
	pb "github.com/apache/fory/benchmarks/go_benchmark/proto"
)

// ToPbStruct converts NumericStruct to protobuf Struct
func ToPbStruct(obj NumericStruct) *pb.Struct {
	return &pb.Struct{
		F1: obj.F1,
		F2: obj.F2,
		F3: obj.F3,
		F4: obj.F4,
		F5: obj.F5,
		F6: obj.F6,
		F7: obj.F7,
		F8: obj.F8,
	}
}

// FromPbStruct converts protobuf Struct to NumericStruct
func FromPbStruct(pb *pb.Struct) NumericStruct {
	return NumericStruct{
		F1: pb.F1,
		F2: pb.F2,
		F3: pb.F3,
		F4: pb.F4,
		F5: pb.F5,
		F6: pb.F6,
		F7: pb.F7,
		F8: pb.F8,
	}
}

// ToPbSample converts Sample to protobuf Sample
func ToPbSample(obj Sample) *pb.Sample {
	return &pb.Sample{
		IntValue:          obj.IntValue,
		LongValue:         obj.LongValue,
		FloatValue:        obj.FloatValue,
		DoubleValue:       obj.DoubleValue,
		ShortValue:        obj.ShortValue,
		CharValue:         obj.CharValue,
		BooleanValue:      obj.BooleanValue,
		IntValueBoxed:     obj.IntValueBoxed,
		LongValueBoxed:    obj.LongValueBoxed,
		FloatValueBoxed:   obj.FloatValueBoxed,
		DoubleValueBoxed:  obj.DoubleValueBoxed,
		ShortValueBoxed:   obj.ShortValueBoxed,
		CharValueBoxed:    obj.CharValueBoxed,
		BooleanValueBoxed: obj.BooleanValueBoxed,
		IntArray:          obj.IntArray,
		LongArray:         obj.LongArray,
		FloatArray:        obj.FloatArray,
		DoubleArray:       obj.DoubleArray,
		ShortArray:        obj.ShortArray,
		CharArray:         obj.CharArray,
		BooleanArray:      obj.BooleanArray,
		String_:           obj.String,
	}
}

// FromPbSample converts protobuf Sample to Sample
func FromPbSample(pb *pb.Sample) Sample {
	return Sample{
		IntValue:          pb.IntValue,
		LongValue:         pb.LongValue,
		FloatValue:        pb.FloatValue,
		DoubleValue:       pb.DoubleValue,
		ShortValue:        pb.ShortValue,
		CharValue:         pb.CharValue,
		BooleanValue:      pb.BooleanValue,
		IntValueBoxed:     pb.IntValueBoxed,
		LongValueBoxed:    pb.LongValueBoxed,
		FloatValueBoxed:   pb.FloatValueBoxed,
		DoubleValueBoxed:  pb.DoubleValueBoxed,
		ShortValueBoxed:   pb.ShortValueBoxed,
		CharValueBoxed:    pb.CharValueBoxed,
		BooleanValueBoxed: pb.BooleanValueBoxed,
		IntArray:          pb.IntArray,
		LongArray:         pb.LongArray,
		FloatArray:        pb.FloatArray,
		DoubleArray:       pb.DoubleArray,
		ShortArray:        pb.ShortArray,
		CharArray:         pb.CharArray,
		BooleanArray:      pb.BooleanArray,
		String:            pb.String_,
	}
}

// ToPbImage converts Image to protobuf Image
func ToPbImage(obj Image) *pb.Image {
	pbImg := &pb.Image{
		Uri:    obj.URI,
		Width:  obj.Width,
		Height: obj.Height,
		Size:   pb.Size(obj.Size),
	}
	if obj.Title != "" {
		pbImg.Title = &obj.Title
	}
	return pbImg
}

// FromPbImage converts protobuf Image to Image
func FromPbImage(pbImg *pb.Image) Image {
	title := ""
	if pbImg.Title != nil {
		title = *pbImg.Title
	}
	return Image{
		URI:    pbImg.Uri,
		Title:  title,
		Width:  pbImg.Width,
		Height: pbImg.Height,
		Size:   Size(pbImg.Size),
	}
}

// ToPbMedia converts Media to protobuf Media
func ToPbMedia(obj Media) *pb.Media {
	pbMedia := &pb.Media{
		Uri:        obj.URI,
		Width:      obj.Width,
		Height:     obj.Height,
		Format:     obj.Format,
		Duration:   obj.Duration,
		Size:       obj.Size,
		Bitrate:    obj.Bitrate,
		HasBitrate: obj.HasBitrate,
		Persons:    obj.Persons,
		Player:     pb.Player(obj.Player),
		Copyright:  obj.Copyright,
	}
	if obj.Title != "" {
		pbMedia.Title = &obj.Title
	}
	return pbMedia
}

// FromPbMedia converts protobuf Media to Media
func FromPbMedia(pbMedia *pb.Media) Media {
	title := ""
	if pbMedia.Title != nil {
		title = *pbMedia.Title
	}
	return Media{
		URI:        pbMedia.Uri,
		Title:      title,
		Width:      pbMedia.Width,
		Height:     pbMedia.Height,
		Format:     pbMedia.Format,
		Duration:   pbMedia.Duration,
		Size:       pbMedia.Size,
		Bitrate:    pbMedia.Bitrate,
		HasBitrate: pbMedia.HasBitrate,
		Persons:    pbMedia.Persons,
		Player:     Player(pbMedia.Player),
		Copyright:  pbMedia.Copyright,
	}
}

// ToPbMediaContent converts MediaContent to protobuf MediaContent
func ToPbMediaContent(obj MediaContent) *pb.MediaContent {
	images := make([]*pb.Image, len(obj.Images))
	for i, img := range obj.Images {
		images[i] = ToPbImage(img)
	}
	return &pb.MediaContent{
		Media:  ToPbMedia(obj.Media),
		Images: images,
	}
}

// FromPbMediaContent converts protobuf MediaContent to MediaContent
func FromPbMediaContent(pbMC *pb.MediaContent) MediaContent {
	images := make([]Image, len(pbMC.Images))
	for i, pbImg := range pbMC.Images {
		images[i] = FromPbImage(pbImg)
	}
	return MediaContent{
		Media:  FromPbMedia(pbMC.Media),
		Images: images,
	}
}
