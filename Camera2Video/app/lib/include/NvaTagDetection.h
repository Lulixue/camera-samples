#ifndef MS_NVA_TAG_DETECTION_H_
#define MS_NVA_TAG_DETECTION_H_

#include "NvaCommon.h"
#include <inttypes.h>

enum ENvaImageFormat {
    keNvImgFmt_YUV420P, // planar YUV 4:2:0, 12bpp
    keNvImgFmt_NV12,    // planar YUV 4:2:0, 12bpp
    keNvImgFmt_NV21,    // as above, but U and V bytes are swapped
    keNvImgFmt_RGBA8,   // packed RGBA 8:8:8:8, 32bpp
};

struct SNvaImageBufferRep {
    void *data[4]; // plane's data pointer
    int pitch[4];  // plane's data stride
    ENvaImageFormat format;
    uint32_t width;
    uint32_t height;
    uint64_t timeStamp; // time in us
};

struct SNvaModelPath {
    const char* scene;
    const char* place;
    const char* photo;
    const char* activity;
};

MS_SDK_API void* CreateTagDetectionSession(const SNvaModelPath& models, const char* outfile);

MS_SDK_API int DetectTag(void* handle, const SNvaImageBufferRep& im);

MS_SDK_API int DetectTag3(void* handle, const SNvaImageBufferRep& im0, const SNvaImageBufferRep& im1, const SNvaImageBufferRep& im2);

MS_SDK_API void CloseTagDetectionSession(void* handle);

#endif

