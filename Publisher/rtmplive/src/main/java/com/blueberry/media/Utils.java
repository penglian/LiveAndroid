package com.blueberry.media;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Created by penglian on 2017/12/20.
 */

public class Utils {
    //判断屏幕方向
    public static boolean isScreenOriatationPortrait(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }
}
