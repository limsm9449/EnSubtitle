package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import java.util.ArrayList;

public class DramaActivity extends AppCompatActivity implements View.OnClickListener {

    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private DramaCursorAdapter adapter;
    private ListView listView;
    private SeekBar seekBar;
    public String code;

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

        getListView();

        AdView av = (AdView)this.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        av.loadAd(adRequest);
    }

    public void getListView() {
        Cursor cursor = db.rawQuery(DicQuery.getSubtitleList(code), null);
        listView = (ListView) this.findViewById(R.id.my_c_lv);
        adapter = new DramaCursorAdapter(getApplicationContext(), cursor, this);
        listView.setAdapter(adapter);
        listView.setFastScrollAlwaysVisible(true);
        listView.setFastScrollEnabled(true);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Cursor cur = (Cursor) adapter.getItem(i);

                Bundle bundle = new Bundle();
                bundle.putString("foreign", cur.getString(cur.getColumnIndexOrThrow("LANG_FOREIGN")));
                bundle.putString("han", cur.getString(cur.getColumnIndexOrThrow("LANG_HAN")));
                bundle.putString("sampleSeq", cur.getString(cur.getColumnIndexOrThrow("SEQ")));

                Intent intent = new Intent(getApplication(), SentenceViewActivity.class);
                intent.putExtras(bundle);

                startActivity(intent);
            }
        });
        listView.setSelection(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 상단 메뉴 구성
        getMenuInflater().inflate(R.menu.menu_help, menu);

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
        }
    }
}

class DramaCursorAdapter extends CursorAdapter {
    private String lang = "A";

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
        ((TextView) view.findViewById(R.id.my_tv_han)).setText(cursor.getPosition() + " " + String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("LANG_HAN"))));
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
    }

    public void setLang(String lang) {
        this.lang = lang;

        notifyDataSetChanged();
    }

}