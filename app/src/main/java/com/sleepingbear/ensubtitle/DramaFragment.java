package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;


public class DramaFragment extends Fragment {
    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private View mainView;
    private DramaFragCursorAdapter adapter;

    public Spinner s_group;
    public String groupCode = "D001";

    private Cursor cursor;

    public DramaFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mainView = inflater.inflate(R.layout.fragment_drama, container, false);

        dbHelper = new DbHelper(getContext());
        db = dbHelper.getWritableDatabase();

        Cursor cursor = db.rawQuery(DicQuery.getDramaList(), null);
        String[] from = new String[]{"CODE_NAME"};
        int[] to = new int[]{android.R.id.text1};
        SimpleCursorAdapter mAdapter = new SimpleCursorAdapter(getContext(), android.R.layout.simple_spinner_item, cursor, from, to);
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s_group = (Spinner) mainView.findViewById(R.id.my_f_s_drama);
        s_group.setAdapter(mAdapter);
        s_group.setSelection(0);
        s_group.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                groupCode = ((Cursor) s_group.getSelectedItem()).getString(1);
                DicUtils.dicLog("groupCode : " + groupCode);

                changeListView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //리스트 내용 변경
        changeListView();

        AdView av = (AdView)mainView.findViewById(R.id.adView);
        AdRequest adRequest = new  AdRequest.Builder().build();
        av.loadAd(adRequest);

        return mainView;
    }

    public void changeListView() {
        cursor = db.rawQuery(DicQuery.getDramaSubList(groupCode), null);

        if ( cursor.getCount() == 0 ) {
            Toast.makeText(getContext(), "검색된 데이타가 없습니다.", Toast.LENGTH_SHORT).show();
        }

        ListView listView = (ListView) mainView.findViewById(R.id.my_f_lv);
        adapter = new DramaFragCursorAdapter(getContext(), cursor, 0);
        listView.setAdapter(adapter);

        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(itemClickListener);
        listView.setSelection(0);
    }

    AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Cursor cur = (Cursor) adapter.getItem(position);

            Bundle bundle = new Bundle();
            bundle.putString("CODE", cur.getString(cur.getColumnIndexOrThrow("CODE")));
            bundle.putString("CODE_NAME", cur.getString(cur.getColumnIndexOrThrow("CODE_NAME")));
            bundle.putString("SMI_FILE", cur.getString(cur.getColumnIndexOrThrow("SMI_FILE")));
            bundle.putString("MP3_FILE", cur.getString(cur.getColumnIndexOrThrow("MP3_FILE")));

            Intent intent = new Intent(getActivity().getApplication(), DramaActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    };

    AdapterView.OnItemLongClickListener itemLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            /*
            Cursor cur = (Cursor) adapter.getItem(position);

            Bundle bundle = new Bundle();
            bundle.putString("kind", "PATTERN");
            bundle.putString("sqlWhere", cur.getString(cur.getColumnIndexOrThrow("SQL_WHERE")));

            Intent intent = new Intent(getActivity().getApplicationContext(), NoteStudyActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
            */

            return true;
        }
    };
}

class DramaFragCursorAdapter extends CursorAdapter {

    public DramaFragCursorAdapter(Context context, Cursor cursor, int flags) {
        super(context, cursor, 0);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.fragment_drama_item, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ((TextView) view.findViewById(R.id.my_tv_drama)).setText(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("CODE_NAME"))));
        ((TextView) view.findViewById(R.id.my_tv_smi)).setText(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("SMI_FILE"))));
    }

}
