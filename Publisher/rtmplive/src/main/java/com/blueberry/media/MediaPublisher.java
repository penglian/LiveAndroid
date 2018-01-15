package com.blueberry.media;

import android.app.Activity;
import android.media.MediaCodec;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Created by blueberry on 3/7/2017.
 * LibRtmp:h264以及AAC推送
 * 不论向 RTMP 服务器推送音频还是视频，都需要按照 FLV 的格式进行封包。因此，在我们向服务器推送第一个 AAC 或 H264 数据包之前，
 * 需要首先推送一个音频 Tag [AAC Sequence Header] 以下简称“音频同步包”，或者视频 Tag [AVC Sequence Header] 以下简称“视频同步包”。
 */

public class MediaPublisher {
    private static final String TAG = "MediaPublisher";

    private Config mConfig;

    public static final int NAL_SLICE = 1;
    public static final int NAL_SLICE_DPA = 2;
    public static final int NAL_SLICE_DPB = 3;
    public static final int NAL_SLICE_DPC = 4;
    public static final int NAL_SLICE_IDR = 5;
    public static final int NAL_SEI = 6;
    public static final int NAL_SPS = 7;
    public static final int NAL_PPS = 8;
    public static final int NAL_AUD = 9;
    public static final int NAL_FILLER = 12;
    //LinkedBlockingQueue是一个线程安全的阻塞队列，实现了先进先出等特性，是作为生产者消费者的首选，可以指定容量，也可以不指定，
    // 不指定的话默认最大是Integer.MAX_VALUE，其中主要用到put和take方法，put方法将一个对象放到队列尾部，在队列满的时候会阻塞
    // 直到有队列成员被消费，take方法从head取一个对象，在队列为空的时候会阻塞，直到有队列成员被放进来。
    private LinkedBlockingQueue<Runnable> mRunnables = new LinkedBlockingQueue<>();
    private Thread workThread;

    private VideoGatherer mVideoGatherer;
    private AudioGatherer mAudioGatherer;
    private MediaEncoder mMediaEncoder;

    private RtmpPublisher mRtmpPublisher;
    public boolean isPublish;

    private AudioGatherer.Params audioParams;
    private VideoGatherer.Params videoParams;
    private boolean loop;


    public static MediaPublisher newInstance(Config config) {
        return new MediaPublisher(config);
    }

    private MediaPublisher(Config config) {
        this.mConfig = config;
    }

    /**
     * 初始化视频采集器，音频采集器，视频编码器，音频编码器
     */
    public void init() {
        mVideoGatherer = VideoGatherer
                .newInstance(mConfig);
        mAudioGatherer = AudioGatherer.newInstance(mConfig);
        mMediaEncoder = MediaEncoder.newInstance(mConfig);
        mRtmpPublisher = RtmpPublisher.newInstance();
        setListener();

        workThread = new Thread() {
            @Override
            public void run() {
                while (loop && !Thread.interrupted()) {
                    try {
                        Runnable runnable = mRunnables.take();
                        runnable.run();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        loop = true;
        workThread.start();
    }

    /**
     * 初始化音频采集器
     */
    public void initAudioGatherer() {
        audioParams = mAudioGatherer.initAudioDevice();
    }

    /**
     * 初始化摄像头
     *
     * @param act
     * @param holder
     */
    public void initVideoGatherer(Activity act, SurfaceHolder holder) {
        videoParams = mVideoGatherer.initCamera(act, holder);
    }

    /**
     * 开始采集
     */
    public void startGather() {
        mAudioGatherer.start();
    }

    /**
     * 初始化编码器
     */
    public void initEncoders(Activity activity) {
        try {
            mMediaEncoder.initAudioEncoder(audioParams.sampleRate, audioParams.channelCount);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "初始化音频编码器失败");
        }

        try {
            int colorFormat;
            if (Utils.isScreenOriatationPortrait(activity)) {
                //由于对yuv进行了90旋转，宽高需要对调设置
                colorFormat = mMediaEncoder.initVideoEncoder(videoParams.previewHeight, videoParams.previewWidth,
                        mConfig.fps);
            } else {
                //不做处理
                colorFormat = mMediaEncoder.initVideoEncoder(videoParams.previewWidth, videoParams.previewHeight,
                        mConfig.fps);
            }
            mVideoGatherer.setColorFormat(colorFormat);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始编码
     */
    public void startEncoder() {
        mMediaEncoder.start();
    }

    /**
     * 发布
     */
    public void starPublish() {
        if (isPublish) {
            return;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                //初始化
                int ret = mRtmpPublisher.init(mConfig.publishUrl,
                        videoParams.previewWidth,
                        videoParams.previewHeight, mConfig.timeOut);
                if (ret < 0) {
                    Log.e(TAG, "连接失败");
                    return;
                }

                isPublish = true;
            }
        };
        mRunnables.add(runnable);
    }


    /**
     * 停止发布
     */
    public void stopPublish() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mRtmpPublisher.stop();
                loop = false;
                workThread.interrupt();
            }
        };
        mRunnables.add(runnable);
    }

    /**
     * 停止编码
     */
    public void stopEncoder() {
        mMediaEncoder.stop();
    }

    /**
     * 停止采集
     */
    public void stopGather() {
        mAudioGatherer.stop();
    }

    /**
     * 释放
     */
    public void release() {
        Log.i(TAG, "release: ");
        mMediaEncoder.release();
        mVideoGatherer.release();
        mAudioGatherer.release();

        loop = false;
        if (workThread != null) {
            workThread.interrupt();
        }
    }

    private void setListener() {
        mVideoGatherer.setCallback(new VideoGatherer.Callback() {
            @Override
            public void onReceive(byte[] data, int colorFormat) {
                if (isPublish) {
                    mMediaEncoder.putVideoData(data);
                }
            }
        });

        mAudioGatherer.setCallback(new AudioGatherer.Callback() {
            @Override
            public void audioData(byte[] data) {
                if (isPublish) {
                    mMediaEncoder.putAudioData(data);
                }
            }
        });

        mMediaEncoder.setCallback(new MediaEncoder.Callback() {
            @Override
            public void outputVideoData(ByteBuffer bb, MediaCodec.BufferInfo info) {
                onEncodedAvcFrame(bb, info);
            }

            @Override
            public void outputAudioData(ByteBuffer bb, MediaCodec.BufferInfo info) {
                onEncodeAacFrame(bb, info);
            }
        });
    }

    /**
     * H264编码   区别PPS、SPS（同步包）与NAL_SLICE、NAL_SLICE_IDR发送，
     *
     * @param bb
     * @param vBufferInfo
     */
    private void onEncodedAvcFrame(ByteBuffer bb, final MediaCodec.BufferInfo vBufferInfo) {
        //帧类型的方式判断为界面符后首字节的低四位。
        int offset = 4;
        //判断帧的类型
        if (bb.get(2) == 0x01) {
            offset = 3;
        }
        int type = bb.get(offset) & 0x1f;
        Log.d(TAG, "type=" + type);
        //打印发现这里将 SPS帧和 PPS帧合在了一起发送
        if (type == NAL_SPS) {
            //[0, 0, 0, 1, 103, 66, -64, 13, -38, 5, -126, 90, 1, -31, 16, -115, 64, 0, 0, 0, 1, 104, -50, 6, -30]
            // SPS为 [4，len-8]
            // PPS为后4个字节
            final byte[] pps = new byte[4];
            final byte[] sps = new byte[vBufferInfo.size - 12];
            bb.getInt();// 抛弃 0,0,0,1
            bb.get(sps, 0, sps.length);
            bb.getInt();
            bb.get(pps, 0, pps.length);
            Log.d(TAG, "解析得到 sps:" + Arrays.toString(sps) + ",PPS=" + Arrays.toString(pps));
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mRtmpPublisher.sendSpsAndPps(sps, sps.length, pps, pps.length,
                            vBufferInfo.presentationTimeUs / 1000);
                }
            };
            try {
                mRunnables.put(runnable);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else if (type == NAL_SLICE || type == NAL_SLICE_IDR) {
            final byte[] bytes = new byte[vBufferInfo.size];
            bb.get(bytes);
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mRtmpPublisher.sendVideoData(bytes, bytes.length,
                            vBufferInfo.presentationTimeUs / 1000);
                }
            };
            try {
                mRunnables.put(runnable);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }

    /**
     * aac编码 ADTS格式 先发关键帧（同步包）,之后再发送基础包
     *
     * @param bb
     * @param aBufferInfo
     */
    private void onEncodeAacFrame(ByteBuffer bb, final MediaCodec.BufferInfo aBufferInfo) {
        if (aBufferInfo.size == 2) {
            // 我打印发现，这里应该已经是吧关键帧计算好了，所以我们直接发送
            final byte[] bytes = new byte[2];
            bb.get(bytes);
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mRtmpPublisher.sendAacSpec(bytes, 2);
                }
            };
            try {
                mRunnables.put(runnable);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            final byte[] bytes = new byte[aBufferInfo.size];
            bb.get(bytes);

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mRtmpPublisher.sendAacData(bytes, bytes.length, aBufferInfo.presentationTimeUs / 1000);
                }
            };
            try {
                mRunnables.put(runnable);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


}
