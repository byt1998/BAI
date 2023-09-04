// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#ifndef YOLO_H
#define YOLO_H

#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <stdio.h>
#include <vector>

#include <net.h>

struct Object
{
    cv::Rect_<float> rect;
//    float x;
//    float y;
//    float w;
//    float h;
    int label;
    float prob;
};
struct GridAndStride
{
    int grid0;
    int grid1;
    int stride;
};
class Yolo
{
public:
    Yolo(AAssetManager *mgr, const char *param, const char *bin, bool useGPU);

    ~Yolo();

    std::vector<Object> detect(JNIEnv *env, jobject image, float prob_threshold = 0.45f, float nms_threshold = 0.8f);


private:
    ncnn::Net *yolo;
    int target_size = 640;

public:
    static Yolo *detector;
    static bool hasGPU;
    static bool toUseGPU;
};

#endif // NANODET_H
