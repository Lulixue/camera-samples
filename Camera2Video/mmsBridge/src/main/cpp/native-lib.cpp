#include <jni.h>
#include <string>
#include <dlfcn.h>
#include  <android/log.h>

// log标签

#define  TAG    "mms_api"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)

typedef char* (*TestFunc)();
typedef int (*TranslateFunc)(JNIEnv *env, unsigned char *img1, unsigned char *img2, unsigned char *img3,
                              const int sizes[3], const int widths[3], const int heights[3],
                              unsigned char **out2k, unsigned char **out4k,
                              int out2KImageSize[2], int out4KImageSize[2], int outSize[2]);


void *so_handle = nullptr;
TranslateFunc translateFunc = nullptr;
void init() {
    if (so_handle == nullptr) {
        so_handle = dlopen("libmms_api.so",RTLD_LAZY);
    }
    if (translateFunc == nullptr) {
        translateFunc = (TranslateFunc) dlsym(so_handle, "mms_translate_images");
        if (translateFunc == nullptr) {
            printf("translate null\n");
            exit(0);
        }
    }
}

void closeHandle() {
    if (so_handle != nullptr) {
        dlclose(so_handle);
        so_handle = nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_mmsbridge_MmsBridgeApi_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "hello from JNI";
    return env->NewStringUTF(hello.c_str());
}

int mms_translate_images(JNIEnv *env, unsigned char *img1, unsigned char *img2, unsigned char *img3,
                         const int sizes[3], const int widths[3], const int heights[3],
                         unsigned char **out2k, unsigned char **out4k,
                         int out2KImageSize[2], int out4KImageSize[2], int outSize[2])
{
    LOGD("start translate");
    int _2KSize = sizes[0];
    jbyteArray out2kArray = env->NewByteArray(_2KSize);
    jboolean isCopy = false;
    *out2k = (unsigned char*)env->GetByteArrayElements(out2kArray, &isCopy);
    env->SetByteArrayRegion(out2kArray, 0, _2KSize, (const jbyte *)img1);
    out2KImageSize[0] = 2560;
    out2KImageSize[1] = 720;
    outSize[0] = _2KSize;

    int _4KSize = sizes[1];
    jbyteArray out4kArray = env->NewByteArray(_4KSize);
    env->SetByteArrayRegion(out4kArray, 0, _2KSize, (const jbyte *)img2);
    *out4k = (unsigned char*)env->GetByteArrayElements(out4kArray, &isCopy);
    out4KImageSize[0] = 7680;
    out4KImageSize[1] = 2160;
    outSize[1] = _4KSize;

    LOGD("end translate");
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_mmsbridge_MmsBridgeApi_translateImages(JNIEnv *env, jobject thiz,
                                                        jobject img1, jobject img2,
                                                        jobject img3,  jobject result) {
    unsigned char *buffer2K = nullptr;
    unsigned char *buffer4K = nullptr;
    int out2KImageSize[2];
    int out4kImageSize[2];
    int outSize[2];
    jboolean isCopy = false;

    jclass clazzSize = env->FindClass("android/util/Size");
    jclass clazzImage = env->FindClass("com/example/mmsbridge/TranslateImage");
    jfieldID imageArrayField = env->GetFieldID(clazzImage, "imageArray", "[B");
    jfieldID imageWidthField = env->GetFieldID(clazzImage, "width", "I");
    jfieldID imageHeightField = env->GetFieldID(clazzImage, "height", "I");

    auto img1Array = (jbyteArray)env->GetObjectField(img1, imageArrayField);
    auto img2Array = (jbyteArray)env->GetObjectField(img2, imageArrayField);
    auto img3Array = (jbyteArray)env->GetObjectField(img3, imageArrayField);
    jbyte* img1Buf = env->GetByteArrayElements(img1Array, &isCopy);
    jbyte* img2Buf = env->GetByteArrayElements(img2Array, &isCopy);
    jbyte* img3Buf = env->GetByteArrayElements(img3Array, &isCopy);


    const int sizes[3] = {
            env->GetArrayLength(img1Array),
            env->GetArrayLength(img2Array),
            env->GetArrayLength(img3Array)
    };
    const int widths[3] = {
            env->GetIntField(img1, imageWidthField),
            env->GetIntField(img2, imageWidthField),
            env->GetIntField(img3, imageWidthField)
    };
    const int height[3] = {
            env->GetIntField(img1, imageHeightField),
            env->GetIntField(img2, imageHeightField),
            env->GetIntField(img3, imageHeightField)
    };


    int ret = translateFunc(env, (unsigned char*)img1Buf, (unsigned char*)img2Buf, (unsigned char*)img3Buf,
                              sizes, widths, height,
                              &buffer2K, &buffer4K, out2KImageSize, out4kImageSize, outSize);

    env->ReleaseByteArrayElements(img1Array, img1Buf, 0);
    env->ReleaseByteArrayElements(img2Array, img2Buf, 0);
    env->ReleaseByteArrayElements(img3Array, img3Buf, 0);


    jclass clazz = (env)->FindClass("com/example/mmsbridge/TranslateResult");
    jmethodID methodID = env->GetMethodID(clazzSize, "<init>", "(II)V");
    jfieldID buffer2KField = env->GetFieldID(clazz, "buffer2K", "[B");
    jfieldID size2KField = env->GetFieldID(clazz, "size2K", "Landroid/util/Size;");
    jfieldID size4KField = env->GetFieldID(clazz, "size8K", "Landroid/util/Size;");
    jfieldID buffer4KField = env->GetFieldID(clazz, "buffer8K", "[B");

    jobject size2K = env->NewObject(clazzSize, methodID, out2KImageSize[0], out2KImageSize[1]);
    jobject size4K = env->NewObject(clazzSize, methodID, out4kImageSize[0], out4kImageSize[1]);

    jbyteArray buffer2kValue = env->NewByteArray(outSize[0]);
    env->SetByteArrayRegion(buffer2kValue, 0, outSize[0], (const jbyte *)buffer2K);
    env->SetObjectField(result, buffer2KField, buffer2kValue);
    env->SetObjectField(result, size2KField, size2K);

    jbyteArray buffer4kValue = env->NewByteArray(outSize[1]);
    env->SetByteArrayRegion(buffer4kValue, 0, outSize[1], (const jbyte *)buffer4K);
    env->SetObjectField(result, buffer4KField, buffer4kValue);
    env->SetObjectField(result, size4KField, size4K);

    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mmsbridge_MmsBridgeApi_close(JNIEnv *env, jobject thiz) {
    closeHandle();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mmsbridge_MmsBridgeApi_open(JNIEnv *env, jobject thiz) {
    init();
}