package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static android.R.attr.button;
import static android.R.attr.id;

public class DramaActivity extends AppCompatActivity implements View.OnClickListener {

    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private DramaCursorAdapter adapter;
    private ListView listView;
    private SeekBar seekBar;

    public String code;
    public String mp3File;
    public ArrayList<Integer> timeAl = new ArrayList<Integer>();

    MediaPlayer mp3Player;
    private boolean isSubtitleSync = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drama);
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
        ((CheckBox) findViewById(R.id.my_c_cb_time)).setOnClickListener(this);

        ((ImageView) findViewById(R.id.my_iv_play)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_pause)).setOnClickListener(this);
        ((ImageView) findViewById(R.id.my_iv_stop)).setOnClickListener(this);


        seekBar = ((SeekBar) findViewById(R.id.my_c_seekBar));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if ( fromUser ) {
                    if ( adapter != null ) {
                        listView.setSelection(getPositionForTime());

                        int minute = progress / 60;
                        int sec = progress - minute * 60;
                        ((TextView) findViewById(R.id.my_c_tv_time)).setText((minute < 10 ? "0" : "") + minute + ":" + (sec < 10 ? "0" : "") + sec);
                    }

                    if ( mp3Player != null ) {
                        mp3Player.seekTo(progress);
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

        try {
            if ( "".equals(mp3File) ) {
                ((RadioButton) findViewById(R.id.my_rb_all)).setChecked(true);
                ((CheckBox) findViewById(R.id.my_c_cb_time)).setChecked(false);
                ((RelativeLayout) findViewById(R.id.my_c_rl_seekBar_hidden)).setVisibility(View.GONE);
                ((LinearLayout) findViewById(R.id.my_c_ll_mp3_hidden)).setVisibility(View.GONE);
            } else {
                File file = new File(mp3File);
                if ( file.exists() ) {
                    ((RadioButton) findViewById(R.id.my_rb_foreign)).setChecked(true);
                    ((CheckBox) findViewById(R.id.my_c_cb_time)).setChecked(true);
                    ((RelativeLayout) findViewById(R.id.my_c_rl_seekBar_hidden)).setVisibility(View.VISIBLE);
                    ((LinearLayout) findViewById(R.id.my_c_ll_mp3_hidden)).setVisibility(View.VISIBLE);

                    mp3Player = new MediaPlayer();
                    mp3Player.setDataSource(mp3File);
                    mp3Player.setLooping(false);
                    //mp3Player.start();

                    ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
                    ((ImageView) findViewById(R.id.my_iv_sync_not)).setVisibility(View.GONE);

                    seekBar.setMax(mp3Player.getDuration());
                } else {
                    ((RadioButton) findViewById(R.id.my_rb_all)).setChecked(true);
                    ((CheckBox) findViewById(R.id.my_c_cb_time)).setChecked(false);
                    ((RelativeLayout) findViewById(R.id.my_c_rl_seekBar_hidden)).setVisibility(View.GONE);
                    ((LinearLayout) findViewById(R.id.my_c_ll_mp3_hidden)).setVisibility(View.GONE);

                    Toast.makeText(getApplicationContext(), "MP3 파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch ( Exception e ) {
            Toast.makeText(getApplicationContext(), "MP3 파일 재생시 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }


        AdView av = (AdView)this.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        av.loadAd(adRequest);
    }

    public void getListView() {
        Cursor cursor = db.rawQuery(DicQuery.getSubtitleList(code), null);

        //시작 제어 로직
        for ( int i = 0; i < cursor.getCount(); i++ ) {
            cursor.moveToPosition(i);
            timeAl.add(Integer.parseInt(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("TIME")))));
        }

        if ( mp3Player == null ) {
            String tempTime = String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("TIME")));
            seekBar.setMax(Integer.parseInt(tempTime.substring(0, tempTime.length() - 1)));
        }

        //처음으로 이동
        cursor.moveToFirst();

        listView = (ListView) this.findViewById(R.id.my_c_lv);
        adapter = new DramaCursorAdapter(getApplicationContext(), cursor, this);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Cursor cur = (Cursor) adapter.getItem(i);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cur = (Cursor) adapter.getItem(position);


                return false;
            };
        });
        listView.setSelection(0);

        adapter.setOption(((RadioGroup) findViewById(R.id.my_rg_lang)).getCheckedRadioButtonId());
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
        if ( v.getId() == R.id.my_rb_all || v.getId() == R.id.my_rb_han || v.getId() == R.id.my_rb_foreign ) {
            adapter.setOption(v.getId());
        } else if ( v.getId() == R.id.my_c_cb_time ) {
            if ( ((CheckBox) findViewById(R.id.my_c_cb_time)).isChecked() ) {
                ((RelativeLayout) findViewById(R.id.my_c_rl_seekBar_hidden)).setVisibility(View.VISIBLE);
            } else {
                ((RelativeLayout) findViewById(R.id.my_c_rl_seekBar_hidden)).setVisibility(View.GONE);
            }
        } else if ( v.getId() == R.id.my_iv_play ) {
            mp3Player.start();

            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.VISIBLE);

            Thread();
        } else if ( v.getId() == R.id.my_iv_pause ) {
            mp3Player.pause();

            ((ImageView) findViewById(R.id.my_iv_play)).setVisibility(View.VISIBLE);
            ((ImageView) findViewById(R.id.my_iv_pause)).setVisibility(View.GONE);
        } else if ( v.getId() == R.id.my_iv_stop ) {
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
    }

    public int getPositionForTime() {
        for ( int i = 0; i < timeAl.size(); i++ ) {
            if ( timeAl.get(i) >= seekBar.getProgress() * 10 ) {
                return i;
            }
        }

        return 0;
    }

    public void Thread(){
        Runnable task = new Runnable(){
            public void run(){
                while ( mp3Player.isPlaying() ){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    seekBar.setProgress(mp3Player.getCurrentPosition());

                    if ( isSubtitleSync ) {
                        listView.setSelection(getPositionForTime());
                    }
                }
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }
}

class DramaCursorAdapter extends CursorAdapter {
    public int option = -1;
    public int repeatStart = -1;
    public int repeatEnd = -1;


    public DramaCursorAdapter(Context context, Cursor cursor, Activity activity) {
        super(context, cursor, 0);
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

        if ( option == R.id.my_rb_all ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.VISIBLE);
        } else if ( option == R.id.my_rb_han ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.GONE);
        } else if ( option == R.id.my_rb_foreign ) {
            ((TextView) view.findViewById(R.id.my_tv_han)).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.my_tv_foreign)).setVisibility(View.VISIBLE);
        }

        if ( repeatStart <= cursor.getPosition() && cursor.getPosition() <= repeatEnd ) {
            view.setBackgroundColor(Color.rgb(249, 151, 53));
        } else {
            view.setBackgroundColor(Color.rgb(255, 255, 255));
        }
    }

    public void clearRepeat() {
        this.repeatStart = -1;
        this.repeatEnd = -1;

        notifyDataSetChanged();
    }

    public void setOption(int option) {
        this.option = option;

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
        this.repeatEnd = 9999;

        notifyDataSetChanged();
    }
}