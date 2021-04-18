package com.example.netvideo;

import android.app.ProgressDialog;
import android.media.MediaPlayer;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.URLUtil;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private VideoView mVideoView;
    private TextView tvcache;
    private String remoteUrl;
    private String localUrl;
    private ProgressDialog progressDialog = null;

    private static final int READY_BUFF = 2000 * 1024;
    private static final int CACHE_BUFF = 500 * 1024;

    private boolean isready = false;
    private boolean iserror = false;
    private int errorCnt = 0;
    private int curPosition = 0;
    private long mediaLength = 0;
    private long readSize = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        init();
        playvideo();
    }

    private void findViews() {
        this.mVideoView = (VideoView) findViewById(R.id.video_view);
        this.tvcache = (TextView) findViewById(R.id.tvcache);
    }

    private void init() {

        //this.remoteUrl = "http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4";
        this.remoteUrl = "https://v-cdn.zjol.com.cn/280443.mp4";
        System.out.println("remoteUrl: " + remoteUrl);
        this.localUrl = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/VideoCache/" + System.currentTimeMillis() + ".mp4";
        if (this.remoteUrl == null) {
            finish();
            return;
        }



        mVideoView.setMediaController(new MediaController(this));

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            public void onPrepared(MediaPlayer mediaplayer) {
                dismissProgressDialog();
                mVideoView.seekTo(curPosition);
                mediaplayer.start();
            }
        });

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            public void onCompletion(MediaPlayer mediaplayer) {
                curPosition = 0;
                mVideoView.pause();
            }
        });

        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            public boolean onError(MediaPlayer mediaplayer, int i, int j) {
                iserror = true;
                errorCnt++;
                mVideoView.pause();
                showProgressDialog();
                return true;
            }
        });
    }

    private void showProgressDialog() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                if (progressDialog == null) {
                    progressDialog = ProgressDialog.show(MainActivity.this,
                            "视频缓存", "正在努力加载中 ...", true, false);
                }
            }
        });
    }

    private void dismissProgressDialog() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            }
        });
    }

    private void playvideo() {
        if (!URLUtil.isNetworkUrl(this.remoteUrl)) {
            mVideoView.setVideoPath(this.remoteUrl);
            mVideoView.start();
            return;
        }

        showProgressDialog();

        new Thread(new Runnable() {

            @Override
            public void run() {
                FileOutputStream out = null;
                InputStream is = null;

                try {
                    URL url = new URL(remoteUrl);
                    HttpURLConnection httpConnection = (HttpURLConnection) url
                            .openConnection();

                    if (localUrl == null) {
                        localUrl = Environment.getExternalStorageDirectory()
                                .getAbsolutePath()
                                + "/VideoCache/"
                                + System.currentTimeMillis() + ".mp4";
                    }

                    System.out.println("localUrl: " + localUrl);

                    File cacheFile = new File(localUrl);
                    if(!cacheFile.exists()) cacheFile.createNewFile();

             /*       if (!cacheFile.exists()) {
                        cacheFile.getParentFile().mkdirs();
                        cacheFile.createNewFile();
                    }*/

                    readSize = cacheFile.length();
                    out = new FileOutputStream(cacheFile, true);

                  //  httpConnection.setRequestProperty("User-Agent", "NetFox");
                    httpConnection.setRequestProperty("RANGE", "bytes="
                            + readSize + "-");

                    is = httpConnection.getInputStream();

                    mediaLength = httpConnection.getContentLength();
                    if (mediaLength == -1) {
                        return;
                    }

                    mediaLength += readSize;

                    byte buf[] = new byte[4 * 1024];
                    int size = 0;
                    long lastReadSize = 0;

                    mHandler.sendEmptyMessage(VIDEO_STATE_UPDATE);

                    while ((size = is.read(buf)) != -1) {
                        try {
                            out.write(buf, 0, size);
                            readSize += size;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (!isready) {
                            if ((readSize - lastReadSize) > READY_BUFF) {
                                lastReadSize = readSize;
                                mHandler.sendEmptyMessage(CACHE_VIDEO_READY);
                            }
                        } else {
                            if ((readSize - lastReadSize) > CACHE_BUFF
                                    * (errorCnt + 1)) {
                                lastReadSize = readSize;
                                mHandler.sendEmptyMessage(CACHE_VIDEO_UPDATE);
                            }
                        }
                    }

                    mHandler.sendEmptyMessage(CACHE_VIDEO_END);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            //
                        }
                    }

                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            //
                        }
                    }
                }

            }
        }).start();

    }

    private final static int VIDEO_STATE_UPDATE = 0;
    private final static int CACHE_VIDEO_READY = 1;
    private final static int CACHE_VIDEO_UPDATE = 2;
    private final static int CACHE_VIDEO_END = 3;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case VIDEO_STATE_UPDATE:
                    double cachepercent = readSize * 100.00 / mediaLength * 1.0;
                    String s = String.format("已缓存: [%.2f%%]", cachepercent);

                    if (mVideoView.isPlaying()) {
                        curPosition = mVideoView.getCurrentPosition();
                        int duration = mVideoView.getDuration();
                        duration = duration == 0 ? 1 : duration;

                        double playpercent = curPosition * 100.00 / duration * 1.0;

                        int i = curPosition / 1000;
                        int hour = i / (60 * 60);
                        int minute = i / 60 % 60;
                        int second = i % 60;

                        s += String.format(" 播放: %02d:%02d:%02d [%.2f%%]", hour,
                                minute, second, playpercent);
                    }

                    tvcache.setText(s);

                    mHandler.sendEmptyMessageDelayed(VIDEO_STATE_UPDATE, 1000);
                    break;

                case CACHE_VIDEO_READY:
                    isready = true;
                    mVideoView.setVideoPath(localUrl);
                    mVideoView.start();
                    break;

                case CACHE_VIDEO_UPDATE:
                    if (iserror) {
                        mVideoView.setVideoPath(localUrl);
                        mVideoView.start();
                        iserror = false;
                    }
                    break;

                case CACHE_VIDEO_END:
                    if (iserror) {
                        mVideoView.setVideoPath(localUrl);
                        mVideoView.start();
                        iserror = false;
                    }
                    break;
            }

            super.handleMessage(msg);
        }
    };
}
