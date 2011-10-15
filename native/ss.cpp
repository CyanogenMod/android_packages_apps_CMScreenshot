/*
**
** Copyright 2010, Koushik Dutta
** Copyright 2011, Paul Kocialkowski <contact@paulk.fr>
** Copyright 2011, The CyanogenMod Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**	 http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#define LOG_TAG "CM Screenshot"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <unistd.h>
#include <time.h>

#include <pwd.h>

#include <linux/fb.h>

#include <sys/mman.h>

#include <binder/IMemory.h>
#include <surfaceflinger/SurfaceComposerClient.h>


using namespace android;

struct bmpfile_magic {
  unsigned char magic[2];
};

struct bmpfile_header {
  uint32_t filesz;
  uint16_t creator1;
  uint16_t creator2;
  uint32_t bmp_offset;
};

struct bmpfile_dibheader {
  uint32_t header_sz;
  uint32_t width;
  uint32_t height;
  uint16_t nplanes;
  uint16_t bitspp;
  uint32_t compress_type;
  uint32_t bmp_bytesz;
  uint32_t hres;
  uint32_t vres;
  uint32_t ncolors;
  uint32_t nimpcolors;
};

typedef unsigned char byte;

#define PIXEL_ORDER_RGBA	0x01
#define PIXEL_ORDER_BGRA	0x02
#define PIXEL_ORDER_UNKNOWN	0x03

int get_pixel_order(struct fb_var_screeninfo *vinfo)
{
    /* RGBA */
	if(vinfo->red.offset < vinfo->green.offset)
    {
        LOGD("RGBA");
        return PIXEL_ORDER_RGBA;
    }
    /* BGRA */
    else if(vinfo->blue.offset < vinfo->green.offset)
    {
        LOGD("BGRA");
        return PIXEL_ORDER_BGRA;
    }
    /* UNKNOWN */
    else
    {
        return PIXEL_ORDER_UNKNOWN;
    }
}

void sort_pixels(int pixel_order, void *buf, void *buf_end)
{
    byte *pos; 
    byte tmp;
    switch(pixel_order)
    {
        case PIXEL_ORDER_BGRA:
        break;

        case PIXEL_ORDER_RGBA:
        default:
            pos = (byte *)buf;
            while (pos < (byte *)buf_end)
            {
                tmp = pos[0];
                pos[0] = pos[2];
                pos[2] = tmp;
                pos += 4;
            }
        break;
    }
}

void* tryfbmap(int framebufferHandle, int size)
{
    void *fbPixels = mmap(0, size, PROT_READ, MAP_SHARED, framebufferHandle, 0);
    if(fbPixels == MAP_FAILED)
    {
        LOGD("failed to map memory\n");
        return NULL;
    }
    return fbPixels;
}

static const int format_map[] = {0,32,32,24,16,32,16,16};

int main(int argc, char **argv)
{
    int framebufferHandle = -1;
    int mapSize = 0;

    int ret = 1;

    char ssPath[128];
    int screenshotHandle;

    void* base = NULL;
    uint32_t w = 0;
    uint32_t h = 0;
    uint32_t depth = 0;
    int totalPixels = 0;
    int pixelOrder = PIXEL_ORDER_RGBA;
    
    sprintf(ssPath,"%s/tmpshot.bmp",getenv("EXTERNAL_STORAGE"));

    ScreenshotClient screenshot;

    if (screenshot.update() != NO_ERROR) {
        struct fb_var_screeninfo vinfo;

        LOGD("surfaceflinger capture failed. Falling back to fb and hoping for the best");
        framebufferHandle = open("/dev/graphics/fb0", O_RDONLY);
        if(framebufferHandle < 0)
            return 0;
        if(ioctl(framebufferHandle, FBIOGET_VSCREENINFO, &vinfo) < 0) {
            close(framebufferHandle);
            return 0;
        }
        w = vinfo.xres;
        h = vinfo.yres;
        totalPixels = w * h;
        depth = vinfo.bits_per_pixel;
        mapSize = totalPixels * (depth/8);
        fcntl(framebufferHandle, F_SETFD, FD_CLOEXEC);
        LOGD("Got %dx%dx%d framebuffer",w,h,depth);
        base = tryfbmap(framebufferHandle, mapSize);
        pixelOrder = get_pixel_order(&vinfo);
    } else {
        uint32_t f = screenshot.getFormat();
        base = (void *)screenshot.getPixels();
        w = screenshot.getWidth();
        h = screenshot.getHeight();
        totalPixels = w * h;
        depth = format_map[f];
    }


    int totalMem8888 = totalPixels * 4;
    int *rgbaPixels = (int*)malloc(totalMem8888);
    int *endOfImage = rgbaPixels + h * w;
    int *rgbaPixelsCopy;
    int *curline;
    int lineSize;
    int *srcline;

    if (depth == 16)
    {
        short *baseCursor = (short*)base;
        int *rgbaPixelsCursor = rgbaPixels;
        int *rgbaLast = rgbaPixels + totalPixels;

        LOGV("Working with 16 depth and a mapsize of %d",mapSize);

        for(; rgbaPixelsCursor < rgbaLast; rgbaPixelsCursor++, baseCursor++)
        {
            short pixel = *baseCursor;
            int r = (pixel & 0xF800) << 8;
            int g = (pixel & 0x7E0) << 5;
            int b = (pixel & 0x1F) << 3;
            int color = 0xFF000000 | r | g | b;
            *rgbaPixelsCursor = color;
        }
    }
    else if (depth == 32)
    {
        memcpy(rgbaPixels, base, totalMem8888);

	    sort_pixels(pixelOrder, rgbaPixels, endOfImage);
    }
    else
    {
        ret = 2;
        goto done;
    }

    // flip it upside down!
    rgbaPixelsCopy = (int*)malloc(totalMem8888);
    curline = rgbaPixelsCopy + (h - 1) * w;
    lineSize = 4 * w;
    srcline = rgbaPixels;
    for (; srcline < endOfImage; curline -= w, srcline += w)
    {
        memcpy(curline, srcline, lineSize);
    }
    memcpy(rgbaPixels, rgbaPixelsCopy, totalMem8888);
    free(rgbaPixelsCopy);

    struct bmpfile_magic magic;
    struct bmpfile_header header;
    struct bmpfile_dibheader dibheader;

    magic.magic[0] = 0x42;
    magic.magic[1] = 0x4D;

    header.bmp_offset = sizeof(magic) + sizeof(header) + sizeof(dibheader);
    header.creator1 = 0;
    header.creator2 = 0;
    header.filesz = sizeof(magic) + sizeof(header) + sizeof(dibheader) + totalMem8888;

    dibheader.header_sz = sizeof(dibheader);
    dibheader.width = w;
    dibheader.height = h;
    dibheader.nplanes = 1;
    dibheader.bitspp = 32;
    dibheader.compress_type = 0;
    dibheader.bmp_bytesz = totalMem8888;
    dibheader.hres = w;
    dibheader.vres = h;
    dibheader.ncolors = 0;
    dibheader.nimpcolors = 0;


    screenshotHandle = open(ssPath, O_WRONLY | O_CREAT);
    write(screenshotHandle, &magic, sizeof(magic));
    write(screenshotHandle, &header, sizeof(header));
    write(screenshotHandle, &dibheader, sizeof(dibheader));
    write(screenshotHandle, rgbaPixels, totalMem8888);
    close(screenshotHandle);

    ret = 0;
done:
    if (rgbaPixels != NULL)
        free(rgbaPixels);
    if (framebufferHandle >= 0 && mapSize) {
        munmap(base, mapSize);
        close(framebufferHandle);
    }
    return ret;
}

