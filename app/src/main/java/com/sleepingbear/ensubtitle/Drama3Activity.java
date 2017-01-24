package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Drama3Activity extends AppCompatActivity implements View.OnClickListener  {

    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private Drama3CursorAdapter adapter;
    private ListView listView;
    private SeekBar seekBar;

    public String code;
    public String mp3File;
    public ArrayList<Integer> timeAl = new ArrayList<Integer>();
    private Thread mThread;

    //MediaPlayer mp3Player;
    private boolean isSubtitleSync = true;

    private ExoPlayer mExoPlayer;
    private int RENDERER_COUNT = 300000;
    private int minBufferMs =    250000;
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;
    private MediaCodecAudioTrackRenderer audioRenderer;
    private int oldTime = -1;
    private int seekToTime = -1;

    private int repeatMode = 0;
    private boolean isRepeat = false;
    private int repeatTimeA = 0;
    private int repeatTimeB = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drama3);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setVisibility(View.GONE);

        Bundle b = this.getIntent().getExtras();
        code = b.getString("CODE");
        mp3File = b.getString("MP3_FILE");

        ActionBar ab = (ActionBar) getSupportActionBar();
        ab.hide();
        //ab.setTitle(b.getString("PATTERN"));
        //ab.setHomeButtonEnabled(true);
        //ab.setDisplayHomeAsUpEnabled(true);

        dbHelper = new DbHelper(this);
        db = dbHelper.getWritableDatabase();

        ((RadioButton) findViewById(R.id.my_rb_all)).setOnClickListener(this);
        ((RadioButton) findViewById(R.id.my_rb_han)).setOnClickListener(this);
        ((RadioButton) findViewById(R.id.my_rb_foreign)).setOnClickListener(this);

        ((ImageView) findViewById(R.id.my_iv_play)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_pause)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_stop)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_sync)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_sync_not)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_repeat)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_start_back1)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_start_forward1)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_end_back1)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_end_forward1)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_back5)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_forward5)).setOnClickListener(this);
        ((TextView) findViewById(R.id.my_tv_repeat_start)).setOnClickListener(this);

        ((LinearLayout) findViewById(R.id.my_c_ll_repeat)).setVisibility(View.GONE);

        seekBar = ((SeekBar) findViewById(R.id.my_c_seekBar));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if ( fromUser ) {
                    DicUtils.dicLog("onProgressChanged : " + progress + " : " + (progress / 60) + " : " + (progress - (progress / 60) * 60));

                    int minute = progress / 60;
                    int sec = progress - minute * 60;
                    ((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec);

                    // 현재 자막 위치 배경색 변경
                    int pos = getPositionForTime();
                    adapter.setMp3TimePosition(pos);

                    DicUtils.dicLog("fromUser : " + pos + " : " + adapter.getCursor().getCount() );
                    if ( adapter != null ) {
                        listView.setSelection(pos);
                    }

                    if ( mExoPlayer != null ) {
                        mExoPlayer.seekTo(progress * 1000);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        getListView();

        AdView av = (AdView)this.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        av.loadAd(adRequest);
    }

    public void getListView() {
        Cursor cursor = db.rawQuery(DicQuery.getSubtitleList(code), null);

        for ( int i = 0; i < cursor.getCount(); i++ ) {
            cursor.moveToPosition(i);
            timeAl.add(Integer.parseInt(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("TIME")))));
        }

        //처음으로 이동
        cursor.moveToFirst();

        listView = (ListView) this.findViewById(R.id.my_c_lv);
        adapter = new Drama3CursorAdapter(getApplicationContext(), cursor, this);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Cursor cur = (Cursor) adapter.getItem(i);

                //player 시간
                int time = timeAl.get(cur.getPosition());

                DicUtils.dicLog("시간비교 : " + getTimeStr(oldTime) + " : " + getTimeStr(time));
                if ( oldTime < time ) {
                    mExoPlayer.seekTo(time * 100 );
                    //seekBar.setProgress(time);
                    //oldTime = time;
                } else {
                    /*
                    mExoPlayer.stop();
                    Allocator allocator = new DefaultAllocator(minBufferMs);
                    DataSource dataSource = new DefaultUriDataSource(getApplicationContext(), null, Util.getUserAgent(Drama3Activity.this, "ExoPlayerExtWebMDemo"));
                    Mp3Extractor extractor = new Mp3Extractor();
                    ExtractorSampleSource sampleSource = new ExtractorSampleSource(
                            Uri.fromFile(new File(mp3File)), dataSource, extractor, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
                    audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);
                    mExoPlayer.prepare(audioRenderer);
                    mExoPlayer.setPlayWhenReady(true);
                    seekToTime = time;
                    */
                    //mExoPlayer.setPlayWhenReady(false);
                    //mExoPlayer.seekTo(0);
                    //mExoPlayer.setPlayWhenReady(true);
                    //mExoPlayer.seekTo(time * 100);
                    mExoPlayer.seekTo(time * 100);
                    //seekBar.setProgress(time);
                    //seekToTime = time;

                    DicUtils.dicLog("이전으로 이동");
                }
                oldTime = time;

                //진행바 시간
                //time = time / 10;
                //int minute = time / 60;
                //int sec = time - minute * 60;
                //((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec);

                DicUtils.dicLog("setOnItemClickListener : " + cur.getPosition() + " : " + getTimeStr(time));

                //adapter.setMp3TimePosition(cur.getPosition());
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cur = (Cursor) adapter.getItem(position);

                return false;
            };
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                DicUtils.dicLog("listView scroll : " + adapter.getCursor().getPosition());
                changeSeekBar(adapter.getCursor().getPosition());
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });
        listView.setSelection(0);

        try {
            mExoPlayer = ExoPlayer.Factory.newInstance(1);
            Allocator allocator = new DefaultAllocator(minBufferMs);
            DataSource dataSource = new DefaultUriDataSource(getApplicationContext(), null, Util.getUserAgent(this, "ExoPlayerExtWebMDemo"));
            Mp3Extractor extractor = new Mp3Extractor();
            ExtractorSampleSource sampleSource = new ExtractorSampleSource(
                    Uri.fromFile(new File(mp3File)), dataSource, extractor, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
            audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);
            mExoPlayer.prepare(audioRenderer);
            mExoPlayer.setPlayWhenReady(true);

            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.GONE);

            mp3PlayerThread();

            mExoPlayer.addListener(new ExoPlayer.Listener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    DicUtils.dicLog("onPlayerStateChanged : " + playbackState);
                    if (playbackState == ExoPlayer.STATE_READY) {

                        /*
                        if ( seekToTime > -1 ) {
                            mExoPlayer.seekTo(seekToTime * 100);
                            seekToTime = -1;
                        }
                        */

                        //mp3PlayerThread();

                        adapter.setMp3Play(true);

                        int time = new Long(mExoPlayer.getDuration()).intValue() / 1000;
                        int minute = time / 60;
                        int sec = time - minute * 60;
                        int sec2 = ( time * 1000  - (minute * 60 + sec) * 1000) / 100;
                        ((TextView) findViewById(R.id.my_c_tv_full_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec + "." + sec2);

                        seekBar.setMax(time);
                    }
                }

                @Override
                public void onPlayWhenReadyCommitted() {
                    DicUtils.dicLog("onPlayWhenReadyCommitted" );

                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    DicUtils.dicLog("onPlayerError" );

                }
            });
        } catch ( Exception e ) {
            Toast.makeText(getApplicationContext(), "MP3 파일 재생시 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 상단 메뉴 구성
        getMenuInflater().inflate(R.menu.menu_pattern, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
        } else if (id == R.id.action_help) {
            Bundle bundle = new Bundle();
            bundle.putString("SCREEN", "DRAMA");

            Intent intent = new Intent(getApplication(), HelpActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if ( v.getId() == R.id.my_rb_all ) {
            adapter.setLang("A");
        } else if ( v.getId() == R.id.my_rb_han ) {
            adapter.setLang("H");
        } else if ( v.getId() == R.id.my_rb_foreign ) {
            adapter.setLang("F");
        } else if ( v.getId() == R.id.my_iv_play ) {
            //mp3Player.start();

            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.VISIBLE);

            mExoPlayer.setPlayWhenReady(true);

            mp3PlayerThread();
        } else if ( v.getId() == R.id.my_iv_pause ) {
            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);

            mExoPlayer.setPlayWhenReady(false);
        } else if ( v.getId() == R.id.my_iv_stop ) {
            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);

            mExoPlayer.seekTo(0);
            mExoPlayer.setPlayWhenReady(false);

            mThread.interrupt();

            adapter.setMp3TimePosition(0);
            listView.setSelection(0);
            seekBar.setProgress(0);
        } else if ( v.getId() == R.id.my_iv_sync ) {
            isSubtitleSync = true;

            ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_sync_not)).setVisibility(View.VISIBLE);
        } else if ( v.getId() == R.id.my_iv_sync_not ) {
            isSubtitleSync = false;

            ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_sync_not)).setVisibility(View.GONE);
        } else if ( v.getId() == R.id.my_iv_repeat ) {
            isRepeat = false;
            if ( repeatMode == 0 ) {
                ((ImageView) findViewById(R.id.my_iv_repeat)).setImageResource(R.mipmap.repeat_a);
                repeatTimeA = new Long(mExoPlayer.getCurrentPosition()).intValue();
                adapter.setRepeatStart(getPositionForTime());
                repeatMode = 1;
                ((LinearLayout) findViewById(R.id.my_c_ll_repeat)).setVisibility(View.VISIBLE);
                setRepeatStartTime();
            } else if ( repeatMode == 1 ) {
                ((ImageView) findViewById(R.id.my_iv_repeat)).setImageResource(R.mipmap.repeat_ab);
                repeatTimeB = new Long(mExoPlayer.getCurrentPosition()).intValue();
                adapter.setRepeatEnd(getPositionForTime());
                isRepeat = true;
                repeatMode = 2;
                setRepeatEndTime();
            } else if ( repeatMode == 2 ) {
                ((ImageView) findViewById(R.id.my_iv_repeat)).setImageResource(R.mipmap.repeat);
                repeatTimeA = 0;
                repeatTimeB = 0;
                adapter.clearRepeat();
                repeatMode = 0;
                ((LinearLayout) findViewById(R.id.my_c_ll_repeat)).setVisibility(View.GONE);
            }
        } else if ( v.getId() == R.id.my_iv_start_back1 ) {
            if ( repeatTimeA > 1000 ) {
                repeatTimeA -= 1000;
                setRepeatStartTime();
                mExoPlayer.seekTo(repeatTimeA);
                adapter.setRepeatStart(getPositionForRepeat(repeatTimeA));
            }
        } else if ( v.getId() == R.id.my_iv_start_forward1 ) {
            repeatTimeA += 1000;
            setRepeatStartTime();
            mExoPlayer.seekTo(repeatTimeA);
            adapter.setRepeatStart(getPositionForRepeat(repeatTimeA));
        } else if ( v.getId() == R.id.my_iv_end_back1 ) {
            if ( repeatTimeB > 1000 ) {
                repeatTimeB -= 1000;
                setRepeatEndTime();
                adapter.setRepeatEnd(getPositionForRepeat(repeatTimeB));
            }
        } else if ( v.getId() == R.id.my_iv_end_forward1 ) {
            if ( repeatTimeB > 1000 ) {
                repeatTimeB += 1000;
                setRepeatEndTime();
                adapter.setRepeatEnd(getPositionForRepeat(repeatTimeB));
            }
        } else if ( v.getId() == R.id.my_tv_repeat_start ) {
            mExoPlayer.seekTo(repeatTimeA);
        } else if ( v.getId() == R.id.my_iv_back5 ) {
            mExoPlayer.seekTo(mExoPlayer.getCurrentPosition() - 5 * 1000);
        } else if ( v.getId() == R.id.my_iv_forward5 ) {
            mExoPlayer.seekTo(mExoPlayer.getCurrentPosition() + 5 * 1000);
        }
    }

    public void setRepeatStartTime() {
        int minute = ( repeatTimeA / 1000 ) / 60;
        int sec = ( repeatTimeA / 1000 ) - minute * 60;
        int sec2 = (repeatTimeA  - (minute * 60 + sec) * 1000) / 100;
        ((TextView) findViewById(R.id.my_tv_repeat_start)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec + "." + sec2);
    }

    public void setRepeatEndTime() {
        int minute = ( repeatTimeB / 1000 ) / 60;
        int sec = ( repeatTimeB / 1000 ) - minute * 60;
        int sec2 = (repeatTimeB  - (minute * 60 + sec) * 1000) / 100;
        ((TextView) findViewById(R.id.my_tv_repeat_end)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec + "." + sec2);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if ( mExoPlayer != null ) {
            if (mExoPlayer.isPlayWhenReadyCommitted()) {
                mExoPlayer.stop();
            }
            mExoPlayer.release();
        }

        if ( mThread != null ) {
            mThread.interrupt();
        }
        if ( handler != null ) {
            handler.removeCallbacks(mThread);
        }
    }

    public int getPositionForTime() {
        for ( int i = 0; i < timeAl.size(); i++ ) {
            if ( timeAl.get(i) == mExoPlayer.getCurrentPosition() / 100 ) {
                //if ( timeAl.get(i) >= mp3Player.getCurrentPosition() * 100 ) {
                DicUtils.dicLog("a getPositionForTime : " + i + " : " + getTimeStr(timeAl.get(i)));
                return i;
            } else if ( timeAl.get(i) > mExoPlayer.getCurrentPosition() / 100 ) {
                DicUtils.dicLog("b getPositionForTime : " + i + " : " + getTimeStr(timeAl.get(i)));
                return ( i > 0 ? i - 1 : 0 );
            }
        }

        return 0;
    }

    public int getPositionForRepeat(int time) {
        for ( int i = 0; i < timeAl.size(); i++ ) {
            if ( timeAl.get(i) == time / 100 ) {
                //if ( timeAl.get(i) >= mp3Player.getCurrentPosition() * 100 ) {
                DicUtils.dicLog("a getPositionForRepeat : " + i + " : " + getTimeStr(timeAl.get(i)));
                return i;
            } else if ( timeAl.get(i) > time / 100 ) {
                DicUtils.dicLog("b getPositionForRepeat : " + i + " : " + getTimeStr(timeAl.get(i)));
                return ( i > 0 ? i - 1 : 0 );
            }
        }

        return 0;
    }

    public void mp3PlayerThread(){
        mThread = new Thread(new Runnable() {
            public void run() {
                try {
                    while ( true ) {
                        Thread.sleep(100);

                        Message msg = handler.obtainMessage();
                        handler.sendMessage(msg);
                    }
                } catch ( InterruptedException e ) {
                    //interrupt 시 Thread 종료..
                } finally {
                    //DicUtils.dicLog("Thread InterruptedException Close");
                }
            }
        });
        mThread.start();
    }

    Handler handler = new Handler(){
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            int currentTime = new Long(mExoPlayer.getCurrentPosition()).intValue();

            //DicUtils.dicLog("mp3Player.getCurrentPosition() : " + mp3Player.getCurrentPosition() + getTimeStr(mp3Player.getCurrentPosition() / 1000));
            if ( isRepeat && currentTime >= repeatTimeB ) {
                currentTime = repeatTimeA;
                mExoPlayer.seekTo(repeatTimeA);
            }

            seekBar.setProgress(currentTime / 1000);

            int minute =  ( currentTime / 1000 ) / 60;
            int sec = ( currentTime / 1000 ) - minute * 60;
            int sec2 = ( currentTime  - (minute * 60 + sec) * 1000) / 100;
            ((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec + "." + sec2);

            oldTime = currentTime / 100;

            //자막 시간을 구한다.
            int pos = getPositionForTime();
            adapter.setMp3TimePosition(pos);
            if (isSubtitleSync) {
                if (pos > 3) {
                    listView.setSelection(pos - 3);
                }
            }
        }
    };

    public void changeSeekBar(int pos ) {
        if ( isSubtitleSync ) {
            if (pos < 0) {
                seekBar.setProgress(timeAl.get(0) / 10);
                mExoPlayer.seekTo(timeAl.get(0) * 100);
            } else {
                seekBar.setProgress(timeAl.get(pos) / 10);
                mExoPlayer.seekTo(timeAl.get(pos) * 100);
            }
        }
    }

    public String getTimeStr(int time) {
        int minute = (time/10) / 60;
        int sec = (time/10) - minute * 60;
        int sec2 = time  - (minute * 60 + sec) * 10;

        return (minute < 10 ? "0" : "") + minute + " 분 " + (sec < 10 ? "0" : "") + sec + " 초 " + sec2;
    }
}

class Drama3CursorAdapter extends CursorAdapter {
    private String lang = "F";
    private int repeatStart = -1;
    private int repeatEnd = -1;
    private int mp3TimePosition = -1;
    private Activity activity;
    private boolean isMp3Play = false;

    public Drama3CursorAdapter(Context context, Cursor cursor, Activity activity) {
        super(context, cursor, 0);

        this.activity = activity;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.content_drama_item, parent, false);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        /*
        DicUtils.dicLog(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("TIME"))) + " : " +
                String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_HAN"))) + " : " +
                String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_FOREIGN"))));
                */
        String tempTime = String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("TIME")));
        String timeStr = "";
        if ( tempTime.length() == 1 ) {
            timeStr = "00:00." + tempTime;
        } else {
            int calcTime = Integer.parseInt(tempTime.substring(0, tempTime.length() - 1));
            int minute = calcTime / 60;
            int sec = calcTime - minute * 60;
            timeStr = ( minute < 10 ? "0" : "" ) + minute + ":" + ( sec < 10 ? "0" : "" ) + sec + "." +  tempTime.substring(tempTime.length() - 1, tempTime.length());
        }
        //DicUtils.dicLog(tempTime + " : " + timeStr);

        ((TextView) view.findViewById(R.id.my_tv_time)).setText(timeStr);
        ((TextView) view.findViewById(R.id.my_tv_han)).setText(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_HAN"))));
        ((TextView) view.findViewById(R.id.my_tv_foreign)).setText(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_FOREIGN"))));

        if ( "A".equals(lang) ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.VISIBLE);
        } else if ( "H".equals(lang) ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.GONE);
        } else if ( "F".equals(lang) ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.VISIBLE);
        }

        if ( isMp3Play ) {
            if (repeatStart > -1 && repeatEnd > -1 && repeatStart <= cursor.getPosition() && cursor.getPosition() <= repeatEnd) {
                ((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(228, 33, 48));
                view.setBackgroundColor(Color.rgb(255, 225, 196));
            } else if (repeatStart > -1 && repeatEnd == -1 && repeatStart <= cursor.getPosition() && cursor.getPosition() <= mp3TimePosition) {
                ((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(228, 33, 48));
                view.setBackgroundColor(Color.rgb(255, 225, 196));
            } else {
                ((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(0, 0, 0));
                view.setBackgroundColor(Color.rgb(255, 255, 255));
            }

            if (cursor.getPosition() == mp3TimePosition) {
                //((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(255, 255, 255));
                view.setBackgroundColor(Color.rgb(255, 194, 134));
            }
        }
    }

    public void clearRepeat() {
        this.repeatStart = -1;
        this.repeatEnd = -1;

        notifyDataSetChanged();
    }

    public void setLang(String lang) {
        this.lang = lang;

        notifyDataSetChanged();
    }

    public int getRepeatEnd() {
        return repeatEnd;
    }

    public void setRepeatEnd(int repeatEnd) {
        this.repeatEnd = repeatEnd;

        notifyDataSetChanged();
    }

    public int getRepeatStart() {
        return repeatStart;
    }

    public void setRepeatStart(int repeatStart) {
        this.repeatStart = repeatStart;

        notifyDataSetChanged();
    }

    public void setMp3TimePosition(int mp3TimePosition) {
        this.mp3TimePosition = mp3TimePosition;

        notifyDataSetChanged();
    }

    public void setMp3Play(boolean mp3Play) {
        isMp3Play = mp3Play;
    }

}