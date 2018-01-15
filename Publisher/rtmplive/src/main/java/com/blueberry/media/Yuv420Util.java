package com.blueberry.media;

/**
 * Created by blueberry on 1/5/2017.
 */

public class Yuv420Util {
    /**
     * Nv21:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * VUVU
     * VUVU
     * VUVU
     * VUVU
     * <p>
     * I420:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * UUUU
     * UUUU
     * VVVV
     * VVVV
     * 不管哪种格式，UV都只占Y的四分之一
     *
     * @param data    Nv21数据
     * @param dstData I420(YUV420)数据
     * @param w       宽度
     * @param h       长度
     */
    public static void Nv21ToI420(byte[] data, byte[] dstData, int w, int h) {

        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);
        for (int i = 0; i < size / 4; i++) {
            dstData[size + i] = data[size + i * 2 + 1]; //U
            dstData[size + size / 4 + i] = data[size + i * 2]; //V
        }
    }

    /**
     * 将Nv21数据转换为Yuv420SP数据
     *
     * @param data    Nv21数据
     * @param dstData Yuv420sp数据
     * @param w       宽度
     * @param h       高度
     */
    public static void Nv21ToYuv420SP(byte[] data, byte[] dstData, int w, int h) {
        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);

        for (int i = 0; i < size / 4; i++) {
            dstData[size + i * 2] = data[size + i * 2 + 1]; //U
            dstData[size + i * 2 + 1] = data[size + i * 2]; //V
        }
    }

    /**
     * yuv420旋转90度
     * @param dst
     * @param data
     * @param width
     * @param height
     */
    public static void rotateYUV420Deg90(byte[] dst, byte[] data, int width, int height) {
        int wh = width * height;
        //旋转Y
        int k = 0;
        for(int i = 0; i < width; i++) {
            for(int j = height - 1; j >= 0; j--)
            {
                dst[k] = data[width * j + i];
                k++;
            }
        }
        for(int i = 0; i < width; i += 2) {
            for(int j = height / 2 - 1; j >= 0; j--)
            {
                dst[k] = data[wh+ width * j + i];
                dst[k+1] = data[wh + width * j + i + 1];
                k+=2;
            }
        }
    }
}
