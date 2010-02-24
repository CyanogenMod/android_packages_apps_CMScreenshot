/*
**
** Copyright 2008, The Android Open Source Project
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

void* tryfbmap(int framebufferHandle, int size)
{
	void *fbPixels = mmap(0, size, PROT_READ, MAP_SHARED, framebufferHandle, 0);
	if(fbPixels == MAP_FAILED)	
	{
		printf("failed to map memory\n");
		return NULL;
	}
	return fbPixels;
}

int main(int argc, char **argv)
{
	void *fbPixels = MAP_FAILED;
	int framebufferHandle = -1;
	int mapSize = 0;

	int ret = 1;
	int screenshotHandle = open("/data/data/com.koushikdutta.screenshot/screenshot.bmp", O_WRONLY | O_CREAT);
	if (screenshotHandle < 0)
	{
		printf("failed to open screenshot.raw\n");
		goto done;
	}

	struct fb_var_screeninfo vinfo;
	framebufferHandle = open("/dev/graphics/fb0", O_RDONLY);
	if(framebufferHandle < 0) 
	{
		printf("failed to open /dev/graphics/fb0\n");
		goto done;
	}

	if(ioctl(framebufferHandle, FBIOGET_VSCREENINFO, &vinfo) < 0) 
	{
		printf("failed to open ioctl\n");
		goto done;
	}
	int w = vinfo.xres;
	int h = vinfo.yres;

	printf("device resolution: %dx%d\n", vinfo.xres, vinfo.yres);
	printf("device bpp: %d\n", vinfo.bits_per_pixel);
	fcntl(framebufferHandle, F_SETFD, FD_CLOEXEC);

	int totalPixels = vinfo.xres * vinfo.yres;
	int totalMem8888 = totalPixels * 4;
	int *rgbaPixels = (int*)malloc(totalMem8888);
	int *endOfImage = rgbaPixels + h * w;

	if (vinfo.bits_per_pixel == 16)
	{
		mapSize = totalPixels * 2;
		if ((fbPixels = tryfbmap(framebufferHandle, mapSize)) == NULL)
			goto done;
		short *fbPixelsCursor = (short*)fbPixels;
	
		int *rgbaPixelsCursor = rgbaPixels;
		int *rgbaLast = rgbaPixels + totalPixels;
		for(; rgbaPixelsCursor < rgbaLast; rgbaPixelsCursor++, fbPixelsCursor++)
		{
			short pixel = *fbPixelsCursor;
			int r = (pixel & 0xF800) << 8;
			int g = (pixel & 0x7E0) << 5;
			int b = (pixel & 0x1F) << 3;
			int color = 0xFF000000 | r | g | b;
			*rgbaPixelsCursor = color;
		}
	}
	else if (vinfo.bits_per_pixel == 32)
	{
		mapSize = totalMem8888;
		if ((fbPixels = tryfbmap(framebufferHandle, mapSize)) == NULL)
			goto done;
		memcpy(rgbaPixels, fbPixels, totalMem8888);
		
		byte *pos = (byte *)rgbaPixels;
		while (pos < endOfImage)
		{
			byte tmp = pos[0];
			pos[0] = pos[2];
			pos[2] = tmp;
			pos += 4;
		}
	}
	else
	{
		printf("Unsupported pixel format.\n");
		goto done;
	}
	
	// flip it upside down!
	int *rgbaPixelsCopy = (int*)malloc(totalMem8888);
	int *curline = rgbaPixelsCopy + (h - 1) * w;
	int lineSize = 4 * w;
	int *srcline = rgbaPixels;
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
	printf("offset: %d\n", header.bmp_offset);
	header.creator1 = 0;
	header.creator2 = 0;
	header.filesz = sizeof(magic) + sizeof(header) + sizeof(dibheader) + totalMem8888;
	printf("file size: %d\n", header.filesz);
	
	dibheader.header_sz = sizeof(dibheader);
	dibheader.width = w;
	dibheader.height = h;
	dibheader.nplanes = 1;
	dibheader.bitspp = 32;
	dibheader.compress_type = 0;
	dibheader.bmp_bytesz = totalMem8888;
	dibheader.hres = 2835;
	dibheader.vres = 120;
	dibheader.ncolors = 0;
	dibheader.nimpcolors = 0;
	
	
	write(screenshotHandle, &magic, sizeof(magic));
	write(screenshotHandle, &header, sizeof(header));
	write(screenshotHandle, &dibheader, sizeof(dibheader));
	write(screenshotHandle, rgbaPixels, totalMem8888);

	printf("success\n");	
	ret = 0;
done:
	if (rgbaPixels != NULL)
		free(rgbaPixels);
	if (fbPixels != NULL)
		munmap(fbPixels, mapSize);
	if(framebufferHandle >= 0) 
		close(framebufferHandle);
	close(screenshotHandle);

	return ret;
}

