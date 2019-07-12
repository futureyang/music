package chien.com.music.activity;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import chien.com.music.R;
import chien.com.music.adapter.KugouSongAdapter;
import chien.com.music.adapter.NeteaseCloudSongAdapter;
import chien.com.music.adapter.QQMusicSongAdapter;
import chien.com.music.http.handler.ImageViewCallbackHandler;
import chien.com.music.http.handler.SimpleCallbackHandler;
import chien.com.music.http.request.Kugou;
import chien.com.music.http.request.NeteaseCloud;
import chien.com.music.http.request.QQMusic;
import chien.com.music.http.response.KugouSearchSongResponse;
import chien.com.music.http.response.NeteaseCloudSearchSongResponse;
import chien.com.music.http.response.QQMusicSearchSongResponse;
import chien.com.music.models.SongItem;
import chien.com.music.receiver.DownloadReceiver;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_CODE_DOWNLOAD = 1;

    public OkHttpClient httpClient;
    private MediaPlayer mediaPlayer;
    private boolean seekBarChanging = false;
    private Timer seekBarTimer;
    private SongItem currentPlaySongItem = null;
    private DownloadReceiver downloadReceiver = new DownloadReceiver();
    public String downloadPath;
    private RadioGroup searchType;
    private RecyclerView searchResult;
    private ImageView player_cover;
    private TextView songName;
    private TextView singerName;
    private ImageView playButton;
    private AppCompatSeekBar seekBar;
    private ProgressBar progressBar;
    private Toolbar toolbar;
    private ImageView downloadButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        httpClient = new OkHttpClient.Builder().build();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setLooping(true);
        seekBarTimer = new Timer();
        //注册广播
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        searchType = findViewById(R.id.search_type);
        searchResult = findViewById(R.id.search_result);
        player_cover = findViewById(R.id.player_cover);
        songName = findViewById(R.id.song_name);
        singerName = findViewById(R.id.singer_name);
        playButton = findViewById(R.id.player_button);
        seekBar = findViewById(R.id.player_seek_bar);
        progressBar = findViewById(R.id.progress_bar);
        downloadButton = findViewById(R.id.download_button);
        toolbar = findViewById(R.id.home_toolbar);
        setSupportActionBar(toolbar);
        afterView();
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayButtonClick();
            }
        });
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadButtonClick();
            }
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        //加载下载路径
        File defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        downloadPath = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.preference_download_path_key), defaultPath.getAbsolutePath());
    }

    public void afterView() {
        searchResult.setLayoutManager(new LinearLayoutManager(this));
        searchResult.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!seekBarChanging && fromUser && seekBar.getMax() > 0) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBarChanging = false;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBarChanging = false;
            }
        });
        seekBarTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (seekBar.getMax() > 0 && !seekBarChanging && mediaPlayer != null) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                }
            }
        }, 0, 1000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        MenuItem searchMenuItem = menu.findItem(R.id.search);
        SearchView sv = (SearchView) searchMenuItem.getActionView();
        sv.setQueryHint(getString(R.string.activity_main_search_hint));
        sv.setOnQueryTextListener(this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.setting) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 处理用户搜索请求
     * @param query 用户的搜索
     * @return 是否处理成功
     */
    @Override
    public boolean onQueryTextSubmit(String query) {
        progressBar.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        switch (searchType.getCheckedRadioButtonId()) {
            case R.id.search_type_netease_cloud:
                return searchNeteaseCloud(query);
            case R.id.search_type_kugou:
                return searchKugou(query);
            case R.id.search_type_qq:
                return searchQQ(query);
        }
        return false;
    }

    protected boolean searchNeteaseCloud(String query) {
        Request req = new NeteaseCloud().searchSong(query, 1, 100);
        httpClient.newCall(req).enqueue(new SimpleCallbackHandler<NeteaseCloudSearchSongResponse>(MainActivity.this) {
            @Override
            public void onResult(Call call, final NeteaseCloudSearchSongResponse response) {
                displaySearchResponse(new NeteaseCloudSongAdapter(response, MainActivity.this));
            }
        });
        return true;
    }

    protected boolean searchKugou(String query) {
        Request req = Kugou.searchSong(query, 1, 100);
        httpClient.newCall(req).enqueue(new SimpleCallbackHandler<KugouSearchSongResponse>(MainActivity.this) {
            @Override
            public void onResult(Call call, final KugouSearchSongResponse response) {
                displaySearchResponse(new KugouSongAdapter(response, MainActivity.this));
            }
        });
        return true;
    }

    protected boolean searchQQ(String query) {
        Request req = QQMusic.searchSong(query, 1, 100);
        httpClient.newCall(req).enqueue(new SimpleCallbackHandler<QQMusicSearchSongResponse>(MainActivity.this) {
            @Override
            public void onResult(Call call, final QQMusicSearchSongResponse response) {
                displaySearchResponse(new QQMusicSongAdapter(response, MainActivity.this));
            }
        });
        return true;
    }

    protected void displaySearchResponse(final RecyclerView.Adapter adapter) {
        (this).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //此时已在主线程中，可以更新UI了
                searchResult.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        });

    }

    /**
     * 播放音乐
     * @param songItem 播放的歌曲
     */
    public void playMusic(SongItem songItem) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(songItem.downloadUrl);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    seekBar.setMax(mp.getDuration());
                    mediaPlayer.start();
                    playButton.setImageResource(R.drawable.stop);
                }
            });
        } catch (IOException|NullPointerException e) {
            Toast.makeText(this, R.string.toast_player_url_error, Toast.LENGTH_SHORT).show();
        }
        currentPlaySongItem = songItem;
        songName.setText(songItem.name);
        singerName.setText(songItem.artist);
        if (songItem.albumImage == null) {
            player_cover.setVisibility(View.GONE);
        } else {
            player_cover.setVisibility(View.VISIBLE);
            httpClient.newCall(new Request.Builder().url(songItem.albumImage).build())
                    .enqueue(new ImageViewCallbackHandler(MainActivity.this, player_cover));
        }
    }

    @Override
    protected void onPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
        super.onDestroy();
    }

    public void onPlayButtonClick() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playButton.setImageResource(R.drawable.play);
        } else if (seekBar.getMax() > 0) {
            mediaPlayer.start();
            playButton.setImageResource(R.drawable.stop);
        }

    }

    public void onDownloadButtonClick() {
        if (currentPlaySongItem == null || TextUtils.isEmpty(currentPlaySongItem.downloadUrl)) {
            return;
        }
        int hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasPermission == PackageManager.PERMISSION_GRANTED) {
            downloadMusic();
        } else {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            boolean showRequest = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            boolean isFirstRequest = sp.getBoolean("is_first_request_download", true);
            if (!isFirstRequest && !showRequest) {
                Toast.makeText(this, R.string.toast_permission_write_fail, Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_DOWNLOAD);
                sp.edit().putBoolean("is_first_request_download", false).apply();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_DOWNLOAD) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadMusic();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void downloadMusic() {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(currentPlaySongItem.downloadUrl));
        Log.i(LOG_TAG, "download path:" + downloadPath);
        req.setDestinationUri(Uri.fromFile(new File(new File(downloadPath), currentPlaySongItem.getFilename())));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setAllowedOverMetered(false);
        req.allowScanningByMediaScanner();
        DownloadManager downloadManager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            long downloadId = downloadManager.enqueue(req);
            downloadReceiver.enqueue(downloadId, currentPlaySongItem);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }
}
