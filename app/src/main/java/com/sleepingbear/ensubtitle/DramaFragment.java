package com.sleepingbear.ensubtitle;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
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

import java.io.File;


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

        ((ImageView) mainView.findViewById(R.id.my_iv_group_setting)).setOnClickListener(this);

        //리스트 내용 변경
        changeListView();

        AdView av = (AdView)mainView.findViewById(R.id.adView);
        AdRequest adRequest = new  AdRequest.Builder().build();
        av.loadAd(adRequest);

        return mainView;
    }

    public void changeDramaCategory() {
        Cursor cursor = db.rawQuery(DicQuery.getDramaGroupAllList(), null);
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
        listView.setOnItemLongClickListener(itemLongClickListener);
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

            String smi = cur.getString(cur.getColumnIndexOrThrow("SMI_FILE"));
            String mp3 = cur.getString(cur.getColumnIndexOrThrow("MP3_FILE"));

            if ( !"".equals(smi) && "".equals(mp3) ) {
                //자막만 있을 때
                Intent intent = new Intent(getActivity().getApplication(), DramaActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            } else if ( !"".equals(smi) && !"".equals(mp3) ) {
                //자막 MP3가 있을 때
                if ( cur.getPosition() == 0 ) {
                    Intent intent = new Intent(getActivity().getApplication(), Drama2Activity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(getActivity().getApplication(), Drama3Activity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);
                }
            } else if ( "".equals(smi) && !"".equals(mp3) ) {
                //MP3만 있을 때..
                Intent intent = new Intent(getActivity().getApplication(), DramaActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        }
    };

    AdapterView.OnItemLongClickListener itemLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            Cursor cur = (Cursor) adapter.getItem(position);

            //layout 구성
            final View dialog_layout = mInflater.inflate(R.layout.dialog_drama_iud, null);

            //dialog 생성..
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(dialog_layout);
            final AlertDialog alertDialog = builder.create();

            final EditText et_drama_name = ((EditText) dialog_layout.findViewById(R.id.my_et_drama_name));
            et_drama_name.setText(cur.getString(cur.getColumnIndexOrThrow("CODE_NAME")));
            final TextView tv_smi_file = ((TextView) dialog_layout.findViewById(R.id.my_d_tv_smi_file));
            tv_smi_file.setText(cur.getString(cur.getColumnIndexOrThrow("SMI_FILE")));
            final TextView tv_mp3_file = ((TextView) dialog_layout.findViewById(R.id.my_d_tv_mp3_file));
            if ( "".equals(cur.getString(cur.getColumnIndexOrThrow("MP3_FILE"))) ) {
                tv_mp3_file.setText(CommConstants.mp3_msg);
            } else {
                tv_mp3_file.setText(cur.getString(cur.getColumnIndexOrThrow("MP3_FILE")));
            }
            final String old_smi_file = tv_smi_file.getText().toString();
            final String code = cur.getString(cur.getColumnIndexOrThrow("CODE"));
            final String codeGroup = cur.getString(cur.getColumnIndexOrThrow("CODE_GROUP"));

            ((Button) dialog_layout.findViewById(R.id.my_b_smi_find)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileChooser filechooser = new FileChooser(getActivity());
                    filechooser.setFileListener(new FileChooser.FileSelectedListener() {
                        @Override
                        public void fileSelected(final File file) {
                            tv_smi_file.setText(file.getAbsolutePath());
                        }
                    });
                    filechooser.setExtension("smi,srt");
                    filechooser.showDialog();
                }
            });
            ((Button) dialog_layout.findViewById(R.id.my_b_mp3_find)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileChooser filechooser = new FileChooser(getActivity());
                    filechooser.setFileListener(new FileChooser.FileSelectedListener() {
                        @Override
                        public void fileSelected(final File file) {
                            tv_mp3_file.setText(file.getAbsolutePath());
                        }
                    });
                    filechooser.setExtension("mp3");
                    filechooser.showDialog();
                }
            });
            ((Button) dialog_layout.findViewById(R.id.my_b_upd)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("".equals(et_drama_name.getText().toString())) {
                        Toast.makeText(getContext(), "드라마 제목을 입력하세요.", Toast.LENGTH_SHORT).show();
                    }
                    String smi_file = ((TextView) dialog_layout.findViewById(R.id.my_d_tv_smi_file)).getText().toString();
                    if ( smi_file.equals(CommConstants.smi_msg) ) {
                        smi_file = "";
                    }
                    String mp3_file = ((TextView) dialog_layout.findViewById(R.id.my_d_tv_mp3_file)).getText().toString();
                    if ( mp3_file.equals(CommConstants.mp3_msg) ) {
                        mp3_file = "";
                    }

                    alertDialog.dismiss();

                    db.execSQL(DicQuery.getUpdDramaCode(codeGroup, code, et_drama_name.getText().toString(), smi_file, mp3_file ) );

                    //파일을 읽어서 자막 파일을 변환한다.
                    if ( !old_smi_file.equals(tv_smi_file.getText().toString()) ) {
                        SubtitleUtils.subtitleExtract(getContext(), db, (String) v.getTag(), smi_file, true);
                    }

                    changeListView();

                    Toast.makeText(getContext(), "드라마를 수정하였습니다.", Toast.LENGTH_SHORT).show();
                }
            });

            ((Button) dialog_layout.findViewById(R.id.my_b_del)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("알림")
                            .setMessage("삭제된 데이타는 복구할 수 없습니다. 삭제하시겠습니까?")
                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    alertDialog.dismiss();

                                    db.execSQL(DicQuery.getDelCode(codeGroup, code));
                                    db.execSQL(DicQuery.getDelSubtitle(code));

                                    changeDramaCategory();

                                    Toast.makeText(getContext(), "드라마를 삭제하였습니다.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
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

            return true;
        }
    };

    public String getGroupCode() {
        DicUtils.dicLog("groupCode : " + groupCode);
        return groupCode;
    }

    @Override
    public void onClick(View v) {
        if ( v.getId() == R.id.my_iv_group_setting ) {
            //layout 구성
            final View dialog_layout = mInflater.inflate(R.layout.dialog_drama_group_iud, null);

            //dialog 생성..
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(dialog_layout);
            final AlertDialog alertDialog = builder.create();

            final EditText et_ins_category_name = ((EditText) dialog_layout.findViewById(R.id.my_et_ins_category_name));
            ((Button) dialog_layout.findViewById(R.id.my_b_ins)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("".equals(et_ins_category_name.getText().toString())) {
                        Toast.makeText(getContext(), "카테고리명을 입력하세요.", Toast.LENGTH_SHORT).show();
                    }

                    db.execSQL(DicQuery.getInsDramaCode("DRAMA", DicQuery.getMaxDramaGroupCode(db), et_ins_category_name.getText().toString(), "", "" ) );

                    changeDramaCategory();

                    alertDialog.dismiss();

                    Toast.makeText(getContext(), "카테고리를 추가 하였습니다.", Toast.LENGTH_SHORT).show();
                }
            });

            final EditText et_category_name = ((EditText) dialog_layout.findViewById(R.id.my_et_category_name));
            et_category_name.setText(((Cursor) s_group.getSelectedItem()).getString(2));
            ((Button) dialog_layout.findViewById(R.id.my_b_upd)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("".equals(et_category_name.getText().toString())) {
                        Toast.makeText(getContext(), "카테고리명을 입력하세요.", Toast.LENGTH_SHORT).show();
                    }

                    db.execSQL(DicQuery.getUpdDramaCode("DRAMA", ((Cursor) s_group.getSelectedItem()).getString(1), et_category_name.getText().toString(), "", "" ) );

                    changeDramaCategory();

                    alertDialog.dismiss();

                    Toast.makeText(getContext(), "카테고리를 수정 하였습니다.", Toast.LENGTH_SHORT).show();
                }
            });

            ((Button) dialog_layout.findViewById(R.id.my_b_del)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("알림")
                            .setMessage("삭제된 데이타는 복구할 수 없습니다. 삭제하시겠습니까?")
                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if ( ((Cursor) s_group.getSelectedItem()).getInt(3) > 0 ) {
                                        Toast.makeText(getContext(), "드라마가 등록된 카테고리는 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show();
                                    } else {
                                        alertDialog.dismiss();

                                        db.execSQL(DicQuery.getDelCode("DRAMA", ((Cursor) s_group.getSelectedItem()).getString(1)));

                                        changeDramaCategory();

                                        Toast.makeText(getContext(), "드라마를 삭제 하였습니다.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
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
        if ( "".equals(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("SMI_FILE")))) ) {
            ((TextView) view.findViewById(R.id.my_tv_smi)).setText("");
        } else {
            ((TextView) view.findViewById(R.id.my_tv_smi)).setText("SMI");
        }
        if ( "".equals(String.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("MP3_FILE")))) ) {
            ((TextView) view.findViewById(R.id.my_tv_mp3)).setText("");
        } else {
            ((TextView) view.findViewById(R.id.my_tv_mp3)).setText("MP3");
        }
    }

}
