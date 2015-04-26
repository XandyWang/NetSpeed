package com.xandy.netspeed;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class NetService extends Service {
    
    private static String TAG = "NetService";
    
    private OverFlow mOverFlow;
    private static final int UPDATE_NET_DATA = 0;
    
    private static final float CHECK_MOST   = .5f;
    private static final float CHECK_NORMAL = 1.f;
    private static final float CHECK_LOW    = 1.5f;
    
    private float mCheck = CHECK_NORMAL;
    
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if ( UPDATE_NET_DATA == msg.what ) {
            	float speed = ((long) msg.obj ) * 1.f;
            	String speedFmt = "";
            	if( speed < 1024 * mCheck ) {
            		speedFmt = String.format("%.2f B/S", speed / mCheck ) ;
            	} else if( speed < 1024 * 1024 * mCheck ) {
            		speedFmt = String.format("%.2f K/S",  speed / ( 1024 * mCheck ) ) ;
            	} else {
            		speedFmt = String.format("%.2f M/S",  speed / ( 1024 * 1024 * mCheck ) ) ;
            	}
                mOverFlow.mShow.setText(speedFmt);
            }
        }
    };
    
    // 系统流量文件
    public final String NET_FILE = "/proc/self/net/dev";

    // 流量数据
    String[] ethData = { "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0" };
    String[] gprsData = { "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0" };
    String[] wifiData = { "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0" };

    // 用来存储前一个时间点的数据
    String[] data = { "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0" };

    // 以太网
    final String ETHLINE = "eth0";
    // wifi
    final String WIFILINE = "wlan0";
    // gprs
    final String GPRSLINE = "rmnet0";

    /**
     * 定义线程周期性地获取网速
     */
    private Runnable mRunnable = new Runnable() {
        // 每3秒钟获取一次数据，求平均，以减少读取系统文件次数，减少资源消耗
        @Override
        public void run() {
            Log.d(TAG, "mRunnable run");
            refreshNet();
            mHandler.postDelayed(mRunnable, (long)(mCheck * 1000));
        }
    };

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart");
    };

    /**
     * 在服务结束时删除消息队列
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mHandler.removeCallbacks(mRunnable);
        super.onDestroy();
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public void onCreate() {
    	Log.d(TAG, "onCreate");
        super.onCreate();
        mOverFlow = new OverFlow(this);
        mOverFlow.show();
        readNetFile();
        refreshData();
        mHandler.postDelayed(mRunnable, 0);
    }

    /**
     * 读取系统流量文件
     */
    public synchronized void readNetFile() {
        Log.d(TAG, "readNetFile");
        FileReader netFile = null;
        try {
            netFile = new FileReader(NET_FILE);
        } catch (Exception e) {
            e.printStackTrace();
            return ;
        }

        BufferedReader bufReader = new BufferedReader(netFile, 500);
        String line;
        String[] data_temp;
        String[] netData;
        int k;
        Pattern p = Pattern.compile("\\s+");
        // 读取文件，并对读取到的文件进行操作
        try {
            while ( (line = bufReader.readLine()) != null ) {
                data_temp = line.trim().split(":");
                if ( line.contains(ETHLINE) ) {
                	data_temp[1] = p.matcher(data_temp[1].trim()).replaceAll("-");
                	Log.d( TAG, "line = " + data_temp[1] );
                	netData = data_temp[1].split("-");
                    for (k = 0; k < ethData.length && k < netData.length; k++) {
                    	ethData[k] = netData[k];
                    	Log.d(TAG, "ethData " + ethData[k]);
                    }
                } else if ( line.contains(GPRSLINE) ) {
                	data_temp[1] = p.matcher(data_temp[1].trim()).replaceAll("-");
                	Log.d( TAG, "line = " + data_temp[1] );
                    netData = data_temp[1].split("-");
                    for (k = 0; k < gprsData.length; k++) {
                    	gprsData[k] = netData[k];
                    	Log.d(TAG, "gprsData " + gprsData[k]);
                    }
                } else if ( line.contains(WIFILINE) ) {
                	data_temp[1] = p.matcher(data_temp[1].trim()).replaceAll("-");
                	Log.d( TAG, "line = " + data_temp[1] );
                    netData = data_temp[1].split("-");
                    for (k = 0 ; k < wifiData.length; k++) {
                    	wifiData[k] = netData[k];
                    	Log.d(TAG, "wifiData " + wifiData[k]);
                    }
                }
            }
            netFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 实时读取系统流量文件，更新
     */
    private void refreshNet() {
        Log.d(TAG, "refreshNet");
        // 读取系统流量文件
        readNetFile();
        // 计算增量
        long[] delta = new long[12];
        delta[0]  = Long.parseLong(ethData[0]) - Long.parseLong(data[0]);
        delta[1]  = Long.parseLong(ethData[1]) - Long.parseLong(data[1]);
        delta[2]  = Long.parseLong(ethData[8]) - Long.parseLong(data[2]);
        delta[3]  = Long.parseLong(ethData[9]) - Long.parseLong(data[3]);
        
        delta[4]  = Long.parseLong(gprsData[0]) - Long.parseLong(data[4]);
        delta[5]  = Long.parseLong(gprsData[1]) - Long.parseLong(data[5]);
        delta[6]  = Long.parseLong(gprsData[8]) - Long.parseLong(data[6]);
        delta[7]  = Long.parseLong(gprsData[9]) - Long.parseLong(data[7]);
        
        delta[8]  = Long.parseLong(wifiData[0]) - Long.parseLong(data[8]);
        delta[9]  = Long.parseLong(wifiData[1]) - Long.parseLong(data[9]);
        delta[10] = Long.parseLong(wifiData[8]) - Long.parseLong(data[10]);
        delta[11] = Long.parseLong(wifiData[9]) - Long.parseLong(data[11]);
        
        refreshData();

        // 每秒下载的字节数
        long netData = delta[0] + delta[4] + delta[8];
        Message msg = mHandler.obtainMessage();
        msg.what = UPDATE_NET_DATA;
        msg.obj = netData;
        mHandler.sendMessage(msg);
    }
    
    private void refreshData() {
    	data[0] = ethData[0];
        data[1] = ethData[1];
        data[2] = ethData[8];
        data[3] = ethData[9];
        data[4] = gprsData[0];
        data[5] = gprsData[1];
        data[6] = gprsData[8];
        data[7] = gprsData[9];
        data[8] = wifiData[0];
        data[9] = wifiData[1];
        data[10] = wifiData[8];
        data[11] = wifiData[9];
    }
}
