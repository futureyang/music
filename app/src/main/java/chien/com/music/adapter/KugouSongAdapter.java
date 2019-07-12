package chien.com.music.adapter;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import chien.com.music.activity.MainActivity;
import chien.com.music.holder.SongViewHolder;
import chien.com.music.http.handler.SimpleCallbackHandler;
import chien.com.music.http.request.Kugou;
import chien.com.music.http.response.KugouPlaySongResponse;
import chien.com.music.http.response.KugouSearchSongResponse;
import chien.com.music.models.SongItem;
import okhttp3.Call;
import okhttp3.Request;

public class KugouSongAdapter extends RecyclerView.Adapter {

    private KugouSearchSongResponse searchResult;
    private Activity activity;

    public KugouSongAdapter(KugouSearchSongResponse searchResult, Activity activity) {
        this.searchResult = searchResult;
        this.activity = activity;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        final KugouSearchSongResponse.Data.Info song = searchResult.data.info.get(position);
        ((SongViewHolder)holder).name.setText(song.songname);
        ((SongViewHolder)holder).singer.setText(song.singername);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playMusic(song);
            }
        });
    }

    @Override
    public int getItemCount() {
        return searchResult.data.info.size();
    }

    private void playMusic(final KugouSearchSongResponse.Data.Info song) {
        Request req = Kugou.playSong(song.hash);
        final SongItem songItem = new SongItem();
        songItem.name = song.songname;
        songItem.albumImage = null;
        songItem.artist = song.singername;
        songItem.extensionName = "mp3";
        ((MainActivity)activity).httpClient.newCall(req).enqueue(new SimpleCallbackHandler<KugouPlaySongResponse>(activity) {
            @Override
            public void onResult(Call call, KugouPlaySongResponse response) {
                songItem.downloadUrl = response.url;
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
