/*
**
** Copyright 2010, Koushik Dutta
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

static const int format_map[] = {0,32,32,24,16,32,16,16};

int main(int argc, char **argv)
{
    int framebufferHandle = -1;
    int mapSize = 0;

    int ret = 1;

    char ssPath[128];
    int screenshotHandle;

    sprintf(ssPath,"%s/tmpshot.bmp",getenv("EXTERNAL_STORAGE"));

    ScreenshotClient screenshot;
    if (screenshot.update() != NO_ERROR)
        return 0;

    const void* base = screenshot.getPixels();
    uint32_t w = screenshot.getWidth();
    uint32_t h = screenshot.getHeight();
    uint32_t f = screenshot.getFormat();
    uint32_t depth = format_map[f];

    int totalPixels = w * h;
    int totalMem8888 = totalPixels * 4;
    int *rgbaPixels = (int*)malloc(totalMem8888);
    int *endOfImage = rgbaPixels + h * w;
    int *rgbaPixelsCopy;
    int *curline;
    int lineSize;
    int *srcline;

    if (depth == 16)
    {
        mapSize = totalPixels * 2;
        short *baseCursor = (short*)base;

        int *rgbaPixelsCursor = rgbaPixels;
        int *rgbaLast = rgbaPixels + totalPixels;
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
        mapSize = totalMem8888;
        memcpy(rgbaPixels, base, totalMem8888);

        byte *pos = (byte *)rgbaPixels;
        while (pos < (byte *)endOfImage)
        {
            byte tmp = pos[0];
            pos[0] = pos[2];
            pos[2] = tmp;
            pos += 4;
        }
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
    dibheader.bitspp = depth;
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

    return ret;
}

