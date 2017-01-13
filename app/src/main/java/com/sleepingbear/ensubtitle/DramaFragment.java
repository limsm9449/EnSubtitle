package com.sleepingbear.ensubtitle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;


public class DramaFragment extends Fragment implements View.OnClickListener {
    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private View mainView;
    private DramaFragCursorAdapter adapter;
    private LayoutInflater mInflater;

    public Spinner s_group;
    public String groupCode = "D001";

    private Cursor cursor;

    public DramaFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mInflater = inflater;
        mainView = inflater.inflate(R.layout.fragment_drama, container, false);

        dbHelper = new DbHelper(getContext());
        db = dbHelper.getWritableDatabase();

        changeDramaCategory();

        ((ImageView) mainView.findViewById(R.id.my_iv_drama_iud)).setOnClickListener(this);

        //리스트 내용 변경
        changeListView();

        AdView av = (AdView)mainView.findViewById(R.id.adView);
        AdRequest adRequest = new  AdRequest.Builder().build();
        av.loadAd(adRequest);

        return mainView;
    }

    public void changeDramaCategory() {
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

    public String getGroupCode() {
        return groupCode;
    }

    @Override
    public void onClick(View v) {
        if ( v.getId() == R.id.my_iv_drama_iud ) {
            //layout 구성
            final View dialog_layout = mInflater.inflate(R.layout.dialog_drama_group_iud, null);

            //dialog 생성..
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(dialog_layout);
            final AlertDialog alertDialog = builder.create();

            ((Button) dialog_layout.findViewById(R.id.my_b_add)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText et_add_name = ((EditText) dialog_layout.findViewById(R.id.my_et_add_name));

                    if ("".equals(et_add_name.getText().toString())) {
                        Toast.makeText(getContext(), "카테고리 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        alertDialog.dismiss();

                        db.execSQL(DicQuery.getInsCode("DRAMA", (String) v.getTag(), et_add_name.getText().toString()));

                        changeDramaCategory();

                        Toast.makeText(getContext(), "카테고리 이름을 등록하였습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            final EditText et_upd = ((EditText) dialog_layout.findViewById(R.id.my_et_upd_name));
            et_upd.setText(((Cursor) s_group.getSelectedItem()).getString(2));
            ((Button) dialog_layout.findViewById(R.id.my_b_upd)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("".equals(et_upd.getText().toString())) {
                        Toast.makeText(getContext(), "카테고리 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        alertDialog.dismiss();

                        db.execSQL(DicQuery.getUpdCode(groupCode, (String) v.getTag(), et_upd.getText().toString()));

                        changeDramaCategory();

                        Toast.makeText(getContext(), "카테고리 이름을 수정하였습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            ((Button) dialog_layout.findViewById(R.id.my_b_del)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (CommConstants.drama_default_code.equals(groupCode)) {
                        Toast.makeText(getContext(), "기본 카테고리는 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show();
                        alertDialog.dismiss();
                    } else if ( ((Cursor) s_group.getSelectedItem()).getInt(2) > 0 ) {
                            Toast.makeText(getContext(), "카테고리로 등록된 드라마가 있어 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show();
                            alertDialog.dismiss();
                    } else {
                        new AlertDialog.Builder(getActivity())
                                .setTitle("알림")
                                .setMessage("삭제된 데이타는 복구할 수 없습니다. 삭제하시겠습니까?")
                                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();

                                        db.execSQL(DicQuery.getDelCode("DRAMA", groupCode));

                                        changeDramaCategory();

                                        Toast.makeText(getContext(), "카테고리를 삭제하였습니다.", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .show();
                    }
                }
            });

            ((Button) dialog_layout.findViewById(R.id.my_b_close)).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.dismiss();
                        }
                    }
            );

            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }
    }

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
