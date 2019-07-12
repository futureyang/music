package chien.com.music.adapter;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import chien.com.music.activity.MainActivity;
import chien.com.music.holder.SongViewHolder;
import chien.com.music.http.handler.SimpleCallbackHandler;
import chien.com.music.http.request.NeteaseCloud;
import chien.com.music.http.response.NeteaseCloudPlayerUrlResponse;
import chien.com.music.http.response.NeteaseCloudSearchSongResponse;
import chien.com.music.models.SongItem;
import okhttp3.Call;
import okhttp3.Request;

public class NeteaseCloudSongAdapter extends RecyclerView.Adapter {

    private NeteaseCloudSearchSongResponse searchResult;
    private Activity activity;

    public NeteaseCloudSongAdapter(NeteaseCloudSearchSongResponse searchResult, Activity activity) {
        this.searchResult = searchResult;
        this.activity = activity;
        // 过滤没有版权的歌曲
        List<NeteaseCloudSearchSongResponse.Result.SongItem> newList = new ArrayList<>();
        for (NeteaseCloudSearchSongResponse.Result.SongItem song : searchResult.result.songs) {
            if (song.privilege.st >= 0) {
                newList.add(song);
            }
        }
        this.searchResult.result.songs = newList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final NeteaseCloudSearchSongResponse.Result.SongItem song = searchResult.result.songs.get(position);
        ((SongViewHolder)holder).name.setText(song.name);
        ((SongViewHolder)holder).singer.setText(song.ar.get(0).name);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playMusic(song);
            }
        });
    }

    @Override
    public int getItemCount() {
        return searchResult.result.songs.size();
    }

    private void playMusic(final NeteaseCloudSearchSongResponse.Result.SongItem song) {
        Request req = new NeteaseCloud().playerUrl(song.id);
        final SongItem songItem = new SongItem();
        songItem.name = song.name;
        songItem.albumImage = song.al.picUrl;
        songItem.artist = song.ar.get(0).name;
        songItem.extensionName = "mp3";
        songItem.album = song.al.name;
        ((MainActivity)activity).httpClient.newCall(req).enqueue(new SimpleCallbackHandler<NeteaseCloudPlayerUrlResponse>(activity) {
            @Override
            public void onResult(Call call, NeteaseCloudPlayerUrlResponse response) {
                songItem.downloadUrl = response.data.get(0).url;
                songItem.extensionName = response.data.get(0).type;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity)activity).playMusic(songItem);
                    }
                });
            }
        });
    }
}
