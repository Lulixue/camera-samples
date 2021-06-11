

struct ImageSize {
    int width;
    int height;
};
class TranslateResult {
public:
    unsigned char *buffer2K;
    unsigned char *buffer4K;
};

char *get_lib_name();

int mms_translate_images(unsigned char *img1, unsigned char *img2, unsigned char *img3,
                         ImageSize sizes[3], unsigned char **out2k, unsigned char **out4k,
                         ImageSize *size2K, ImageSize *size4K);

int mms_translate_images_base(unsigned char *img1, unsigned char *img2, unsigned char *img3,
                              int widths[3], int heights[3], unsigned char **out2k, unsigned char **out4k,
                              int outWidth[2], int outHeight[2]);