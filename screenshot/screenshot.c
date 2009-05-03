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

#include <cutils/fdevent.h>
#include <linux/fb.h>

#include <sys/mman.h>

int main(int argc, char **argv)
{
	void *fbPixels = MAP_FAILED;
	int totalMem565 = 0;
	int framebufferHandle = -1;

	int ret = 1;
	int screenshotHandle = open("/data/data/com.koushikdutta.screenshot/screenshot.raw", O_WRONLY | O_CREAT);
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
	fcntl(framebufferHandle, F_SETFD, FD_CLOEXEC);

	int totalPixels = vinfo.xres * vinfo.yres;
	totalMem565 = totalPixels * 2;

	fbPixels = mmap(0, totalMem565, PROT_READ, MAP_SHARED, framebufferHandle, 0);
	if(fbPixels == MAP_FAILED)	
	{
		printf("failed to map memory\n");
		goto done;
	}

	int w = vinfo.xres;
	int h = vinfo.yres;
	write(screenshotHandle, &w, 4);
	write(screenshotHandle, &h, 4);
	write(screenshotHandle, fbPixels, totalMem565);

	if (0)
	{
		int totalMem8888 = totalPixels * 4;

		short *fbPixelsCursor = (short*)fbPixels;
		int *rgbaPixels = (int*)malloc(totalMem8888);
	
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

		if(!write(screenshotHandle, rgbaPixels, totalMem8888))
		{
			printf("failed to write to screenshot.raw\n");
			goto done;
		}
		free(rgbaPixels);
	}

	printf("success\n");	
	ret = 0;
done:
	if(fbPixels != MAP_FAILED) 
		munmap(fbPixels, totalMem565);
	if(framebufferHandle >= 0) 
		close(framebufferHandle);
	close(screenshotHandle);

	return ret;
}

