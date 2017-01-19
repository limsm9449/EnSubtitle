package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.MediaPlayer;
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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Drama2Activity extends AppCompatActivity implements View.OnClickListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private Drama2CursorAdapter adapter;
    private ListView listView;
    private SeekBar seekBar;

    public String code;
    public String mp3File;
    public ArrayList<Integer> timeAl = new ArrayList<Integer>();
    private Thread mThread;

    MediaPlayer mp3Player;
    private boolean isSubtitleSync = true;
    private boolean isRepeat = false;
    private String repeatFlag = "N";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drama2);
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


        seekBar = ((SeekBar) findViewById(R.id.my_c_seekBar));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                DicUtils.dicLog("onProgressChanged : " + progress + " : " + (progress / 60) + " : " + (progress - (progress / 60) * 60));

                if ( fromUser ) {
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

                    if ( mp3Player != null ) {
                        mp3Player.seekTo(progress * 1000);
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
        adapter = new Drama2CursorAdapter(getApplicationContext(), cursor, this);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Cursor cur = (Cursor) adapter.getItem(i);

                //player 시간
                int time = timeAl.get(cur.getPosition());
                seekBar.setProgress(time);
                mp3Player.seekTo(time * 100);

                //진행바 시간
                time = time / 10;
                int minute = time / 60;
                int sec = time - minute * 60;
                ((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec);

                DicUtils.dicLog("setOnItemClickListener : " + cur.getPosition() + " : " + getTimeStr(time));

                adapter.setMp3TimePosition(cur.getPosition());
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
            File file = new File(mp3File);
            if ( file.exists() ) {
                ((RadioButton) findViewById(R.id.my_rb_foreign)).setChecked(true);

                mp3Player = new MediaPlayer();
                mp3Player.setOnPreparedListener(this);
                mp3Player.setOnCompletionListener(this);
                mp3Player.setDataSource(mp3File);
                mp3Player.setLooping(false);
                mp3Player.prepareAsync();

                ((ImageView) findViewById(R.id.my_iv_play)).setEnabled(false);
                ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
                ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);
                ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.GONE);

                adapter.setMp3Play(true);
            } else {
                adapter.setMp3Play(false);

                Toast.makeText(getApplicationContext(), "MP3 파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
            }
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
            mp3Player.start();

            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.VISIBLE);

            mp3PlayerThread();
        } else if ( v.getId() == R.id.my_iv_pause ) {
            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);

            mp3Player.pause();
        } else if ( v.getId() == R.id.my_iv_stop ) {
            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);

            mp3Player.stop();

            try {
                mp3Player.prepare();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mp3Player.seekTo(0);

            seekBar.setProgress(0);
        } else if ( v.getId() == R.id.my_iv_sync ) {
            isSubtitleSync = true;

            ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_sync_not)).setVisibility(View.VISIBLE);
        } else if ( v.getId() == R.id.my_iv_sync_not ) {
            isSubtitleSync = false;

            ((ImageView) findViewById(R.id.my_iv_sync)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_sync_not)).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if ( mp3Player != null ) {
            if (mp3Player.isPlaying()) {
                mp3Player.stop();
            }
            mp3Player.release();
        }

        if ( mThread != null ) {
            mThread.interrupt();
        }
        if ( handler != null ) {
            handler.removeCallbacks(mThread);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
        ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
        ((ImageView) findViewById(R.id.my_iv_stop)).setVisibility(View.GONE);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        ((ImageView) findViewById(R.id.my_iv_play)).setEnabled(true);

        //DicUtils.dicLog("mp3Player.getDuration() : " + mp3Player.getDuration());
        seekBar.setMax(mp3Player.getDuration() / 1000);

        //Toast.makeText(getApplicationContext(), "MP3 가 준비되었습니다. Play 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show();
    }

    public int getPositionForTime() {
        for ( int i = 0; i < timeAl.size(); i++ ) {
            if ( timeAl.get(i) >= seekBar.getProgress() * 10 ) {
            //if ( timeAl.get(i) >= mp3Player.getCurrentPosition() * 100 ) {
                //DicUtils.dicLog(" getPositionForTime : " + i + " : " + getTimeStr(timeAl.get(i) / 10));
                return ( i > 0 ? i - 1 : 0 );
            }
        }

        return 0;
    }

    public void mp3PlayerThread(){
        mThread = new Thread(new Runnable() {
            public void run() {
                try {
                    while ( mp3Player.isPlaying() ) {
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

            //DicUtils.dicLog("mp3Player.getCurrentPosition() : " + mp3Player.getCurrentPosition() + getTimeStr(mp3Player.getCurrentPosition() / 1000));
            if ( mp3Player != null ) {
                seekBar.setProgress(mp3Player.getCurrentPosition() / 1000);

                int minute = (mp3Player.getCurrentPosition() / 1000) / 60;
                int sec = (mp3Player.getCurrentPosition() / 1000) - minute * 60;
                int sec2 = (mp3Player.getCurrentPosition() - (minute * 60 + sec) * 1000) / 100;
                ((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec + "." + sec2);

                //자막 시간을 구한다.
                int pos = getPositionForTime();
                adapter.setMp3TimePosition(pos);
                if (isSubtitleSync) {
                    if (pos > 3) {
                        listView.setSelection(pos - 3);
                    }
                }
            }
        }
    };

    public void changeSeekBar(int pos ) {
        if ( isSubtitleSync ) {
            if (pos < 0) {
                seekBar.setProgress(timeAl.get(0) / 10);
                mp3Player.seekTo(timeAl.get(0) * 100);
            } else {
                seekBar.setProgress(timeAl.get(pos) / 10);
                mp3Player.seekTo(timeAl.get(pos) * 100);
            }
        }
    }

    public String getTimeStr(int time) {
        int minute = time / 60;
        int sec = time - minute * 60;
        return (minute < 10 ? "0" : "") + minute + " 분 " + (sec < 10 ? "0" : "") + sec + " 초";
    }
}

class Drama2CursorAdapter extends CursorAdapter {
    private String lang = "F";
    private int repeatStart = -1;
    private int repeatEnd = -1;
    private int mp3TimePosition = -1;
    private Activity activity;
    private boolean isMp3Play = false;

    public Drama2CursorAdapter(Context context, Cursor cursor, Activity activity) {
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
        ((TextView) view.findViewById(R.id.my_tv_han)).setText(cursor.getPosition() + " " + String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_HAN"))));
        ((TextView) view.findViewById(R.id.my_tv_foreign)).setText(cursor.getPosition() + " " + String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_FOREIGN"))));

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
            if (repeatStart <= cursor.getPosition() && cursor.getPosition() <= repeatEnd) {
                view.setBackgroundColor(Color.rgb(249, 151, 53));
            } else {
                view.setBackgroundColor(Color.rgb(255, 255, 255));
            }

            if (cursor.getPosition() == mp3TimePosition) {
                ((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(255, 255, 255));
                view.setBackgroundColor(Color.rgb(55, 55, 55));
            } else {
                ((TextView) view.findViewById(R.id.my_tv_time)).setTextColor(Color.rgb(0, 0, 0));
                view.setBackgroundColor(Color.rgb(255, 255, 255));
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
        this.repeatEnd = repeatStart;

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