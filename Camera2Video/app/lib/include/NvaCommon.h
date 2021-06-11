#ifndef MS_NVA_COMMON_H_
#define MS_NVA_COMMON_H_

#ifdef _MSC_VER
#   ifdef __cplusplus
#       ifdef BUILD_STATIC_LIB
#           define MS_SDK_API  extern "C"
#       else
#           ifdef SDK_IMPORTS
#               define MS_SDK_API extern "C" __declspec(dllimport)
#           else
#               define MS_SDK_API extern "C" __declspec(dllexport)
#           endif
#       endif
#   else
#       ifdef BUILD_STATIC_LIB
#           define MS_SDK_API
#       else
#           ifdef SDK_IMPORTS
#               define MS_SDK_API __declspec(dllimport)
#           else
#               define MS_SDK_API __declspec(dllexport)
#           endif
#       endif
#   endif
#else /* _MSC_VER */
#   ifdef __cplusplus
#       ifdef SDK_IMPORTS
#           define MS_SDK_API extern "C"
#       else
#           define MS_SDK_API extern "C" __attribute__((visibility ("default")))
#       endif
#   else
#       ifdef SDK_IMPORTS
#           define MS_SDK_API
#       else
#           define MS_SDK_API __attribute__((visibility ("default")))
#       endif
#   endif
#endif

#define MS_NO_ERR                           0
#define MS_ERR_OUT_OF_MEMORY               -1
#define MS_ERR_BAD_ARG_VALUE               -2
#define MS_ERR_FAIL                        -3

#endif
