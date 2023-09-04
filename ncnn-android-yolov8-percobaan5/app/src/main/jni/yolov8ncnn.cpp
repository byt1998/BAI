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

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "yolo.h"

//#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>

//#if __ARM_NEON
//#include <arm_neon.h>
//#endif // __ARM_NEON
//
//static int draw_unsupported(cv::Mat& rgb)
//{
//    const char text[] = "unsupported";
//
//    int baseLine = 0;
//    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);
//
//    int y = (rgb.rows - label_size.height) / 2;
//    int x = (rgb.cols - label_size.width) / 2;
//
//    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
//                    cv::Scalar(255, 255, 255), -1);
//
//    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
//                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));
//
//    return 0;
//}
//
//static int draw_fps(cv::Mat& rgb)
//{
//    // resolve moving average
//    float avg_fps = 0.f;
//    {
//        static double t0 = 0.f;
//        static float fps_history[10] = {0.f};
//
//        double t1 = ncnn::get_current_time();
//        if (t0 == 0.f)
//        {
//            t0 = t1;
//            return 0;
//        }
//
//        float fps = 1000.f / (t1 - t0);
//        t0 = t1;
//
//        for (int i = 9; i >= 1; i--)
//        {
//            fps_history[i] = fps_history[i - 1];
//        }
//        fps_history[0] = fps;
//
//        if (fps_history[9] == 0.f)
//        {
//            return 0;
//        }
//
//        for (int i = 0; i < 10; i++)
//        {
//            avg_fps += fps_history[i];
//        }
//        avg_fps /= 10.f;
//    }
//
//    char text[32];
//    sprintf(text, "FPS=%.2f", avg_fps);
//
//    int baseLine = 0;
//    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);
//
//    int y = 0;
//    int x = rgb.cols - label_size.width;
//
//    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
//                    cv::Scalar(255, 255, 255), -1);
//
//    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
//                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));
//
//    return 0;
//}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "YoloV8Ncnn", "JNI_OnLoad");
    ncnn::create_gpu_instance();
    if (ncnn::get_gpu_count() > 0) {
        Yolo::hasGPU =true;
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "YoloV8Ncnn", "JNI_OnUnload");

    ncnn::destroy_gpu_instance();
    delete Yolo::detector;
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
extern "C" JNIEXPORT void JNICALL
Java_bai_bcp_yolov8ncnn_Yolov8Ncnn_loadModel(JNIEnv *env, jclass, jobject assetManager, jboolean useGPU) {
    if (Yolo::detector != nullptr) {
        delete Yolo::detector;
        Yolo::detector = nullptr;
    }
    if (Yolo::detector == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        Yolo::detector = new Yolo(mgr, "best-sim-opt-fp16.param", "best-sim-opt-fp16.bin", useGPU);
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_bai_bcp_yolov8ncnn_Yolov8Ncnn_detect(JNIEnv *env, jclass, jobject image) {
    auto result = Yolo::detector->detect(env, image);

    auto box_cls = env->FindClass("bai/bcp/yolov8ncnn/Box");
    auto cid = env->GetMethodID(box_cls, "<init>", "(FFFFIF)V");
    jobjectArray ret = env->NewObjectArray(result.size(), box_cls, nullptr);
    int i = 0;
    for (auto &box : result) {
        env->PushLocalFrame(1);
        jobject obj = env->NewObject(box_cls, cid, box.rect.x, box.rect.y, box.rect.x + box.rect.width, box.rect.y + box.rect.height, box.label, box.prob);
        obj = env->PopLocalFrame(obj);
        env->SetObjectArrayElement(ret, i++, obj);
    }
    return ret;
}


