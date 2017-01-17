package com.sleepingbear.ensubtitle;


import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class SubtitleUtils {
    private static HashMap<String, HashMap> subtitleHm = new HashMap<String, HashMap>();
    private static ArrayList<Integer> timeAl = new ArrayList<Integer>();

    public static void subtitleExtract(SQLiteDatabase db, String code, String fileName, boolean pIsKor) {
        // 초기화
        subtitleHm.clear();
        timeAl.clear();;

        if ( "SMI".equals(fileName.substring(fileName.length() - 3, fileName.length()).toUpperCase()) ) {
            boolean isKor = readSmiFile(fileName, pIsKor);
            if ( isKor == true ) {
                if ( existFile(fileName.substring(0, fileName.length() - 4) + "_en.smi") ) {
                    readSmiFile(fileName.substring(0, fileName.length() - 4) + "_en.smi", false);
                } else if ( existFile(fileName.substring(0, fileName.length() - 4) + "_en.srt") ) {
                    readSrtFile(fileName.substring(0, fileName.length() - 4) + "_en.srt", false);
                }
            }
        } else {
            readSrtFile(fileName, true);

            if ( existFile(fileName.substring(0, fileName.length() - 4) + "_en.smi") ) {
                readSmiFile(fileName.substring(0, fileName.length() - 4) + "_en.smi", false);
            } else if ( existFile(fileName.substring(0, fileName.length() - 4) + "_en.srt") ) {
                readSrtFile(fileName.substring(0, fileName.length() - 4) + "_en.srt", false);
            }
        }

        Collections.sort(timeAl, new Comparator<Integer>(){
            public int compare(Integer obj1, Integer obj2) {
                return (obj1 < obj2) ? -1: (obj1 > obj2) ? 1:0 ;
            }
        });

        db.execSQL(DicQuery.getDelSubtitle(code));
        for ( int i = 0; i < timeAl.size(); i++ ) {
            //System.out.println(timeAl.get(i) + " : " + subtitleHm.get(Integer.toString(timeAl.get(i))));
            String han = DicUtils.getString((String)subtitleHm.get(Integer.toString(timeAl.get(i))).get("HAN") );
            String foreign = DicUtils.getString((String)subtitleHm.get(Integer.toString(timeAl.get(i))).get("FOREIGN") );

            if ( !"".equals(han) && !"".equals(foreign) ) {
                db.execSQL(DicQuery.getInsSubtitle(code, Integer.toString(timeAl.get(i)),
                        (DicUtils.getString((String) subtitleHm.get(Integer.toString(timeAl.get(i))).get("HAN"))).replaceAll("'", "''"),
                        (DicUtils.getString((String) subtitleHm.get(Integer.toString(timeAl.get(i))).get("FOREIGN"))).replaceAll("'", "''")));
            }
        }
    }

    public static boolean existFile(String filePath) {
        File file = new File(filePath);

        return file.exists();
    }

    public static boolean readSmiFile(String filePath, boolean pIsKor) {
        System.out.println(filePath);
        boolean isStartSync = false;
        boolean isKor = pIsKor;
        String oldClass = "";

        File f = new File(filePath);

        String[] charsetsToBeTested = {"UTF-8", "euc-kr"};
        Charset charset = detectCharset(f, charsetsToBeTested);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), charset));
            String line = null;

            String startTime = "";
            while ((line = br.readLine()) != null) {
                //System.out.println(line);

                if ( isStartSync == false && line.toUpperCase().indexOf("<SYNC") < 0 ) {
                    continue;
                } else {
                    isStartSync = true;
                }

                boolean isSyncLine = false;
                if ( line.toUpperCase().indexOf("<SYNC") > -1 ) {
                    isSyncLine = true;

                    //시간 분리
                    startTime = tagAttributeValue(line.substring(0, line.indexOf(">") + 1), "Start");
                    //System.out.println("startTime : " + startTime);

                    line = line.substring(line.indexOf(">") + 1, line.length());
                }

                String currClass = "";
                if ( line.toUpperCase().indexOf("<P CLASS=") > -1 ) {
                    //class 분리
                    currClass = tagAttributeValue(line.substring(0, line.indexOf(">") + 1), "Class");

                    line = line.substring(line.indexOf(">") + 1, line.length());

                    if ( "".equals(oldClass) ) {
                        oldClass = currClass;
                    }
                    if ( isKor == true && !currClass.equals(oldClass) ) {
                        isKor = false;
                        oldClass = currClass;
                    }
                }


                line = line.replaceAll("&nbsp;", " ").replaceAll("[<][^>]*>", " ").replaceAll("  ", " ").trim();
                if ( isSyncLine ) {
                    if ( isKor == true ) {
                        addKorSubtitle(startTime, line);
                    } else {
                        addForeignSubtitle(startTime, line);
                    }
                } else {
                    if ( isKor == true ) {
                        addKorSubtitle(startTime, line);
                    } else {
                        addForeignSubtitle(startTime, line);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (br!=null)
                    br.close();
            } catch (Exception e) {}
        }

        return isKor;
    }

    public static void readSrtFile(String filePath, boolean isKor) {
        System.out.println(filePath);
        boolean isStartSubtitle = false;

        File f = new File(filePath);

        String[] charsetsToBeTested = {"UTF-8", "euc-kr"};
        Charset charset = detectCharset(f, charsetsToBeTested);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), charset));
            String line = null;

            String startTime = "";
            while ((line = br.readLine()) != null) {
                //System.out.println(line);

                if ( isStartSubtitle == false ) {
                    if ( line.toUpperCase().indexOf("-->") > -1 ) {
                        //시간 분리
                        String tempStartTime = line.substring(0, line.indexOf("-->")).replaceAll("[:, ]", "");

                        //시간이 틀린 경우도 있어서 체크해줌
                        if ( tempStartTime.length() < 9 ) {
                            continue;
                        }
                        startTime = Integer.toString( Integer.parseInt(tempStartTime.substring(0, 2)) * 60 * 60 +
                                Integer.parseInt(tempStartTime.substring(2, 4)) * 60 +
                                Integer.parseInt(tempStartTime.substring(4, 6)) ) + tempStartTime.substring(6, 9);
                        //System.out.println("startTime : " + tempStartTime + " : " + startTime);

                        isStartSubtitle = true;

                        continue;
                    } else {
                        continue;
                    }
                }
                if ( "".equals(line) ) {
                    isStartSubtitle = false;

                    continue;
                }

                line = line.replaceAll("&nbsp;", " ").replaceAll("[<][^>]*>", " ").replaceAll("  ", " ").trim();
                if ( isKor == true ) {
                    addKorSubtitle(Integer.toString(Integer.parseInt(startTime)), line);
                } else {
                    addForeignSubtitle(Integer.toString(Integer.parseInt(startTime)), line);
                }
            }
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        } finally {
            try {
                if (br!=null)
                    br.close();
            } catch (Exception e) {}
        }
    }

    public static void addKorSubtitle(String time, String subtitle) {
        //System.out.println("addKorSubtitle"  + " : " + time  + " : " + subtitle);
        String tempTime = time.substring(0, time.length() - 2);
        if ( subtitleHm.containsKey(tempTime) ) {
            HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get(tempTime);
            row.put("HAN", row.get("HAN") + " " + subtitle);
        } else {
            timeAl.add(Integer.parseInt(tempTime));

            HashMap<String, String> row = new HashMap<String, String>();
            row.put("HAN", subtitle);
            row.put("FOREIGN", "");

            subtitleHm.put(tempTime, row);
        }
    }

    public static void addForeignSubtitle(String time, String subtitle) {
        //System.out.println("addForeignSubtitle"  + " : " + time  + " : " + subtitle);
        String tempTime = time.substring(0, time.length() - 2);
        //System.out.println("tempTime : " + tempTime);
        if ( subtitleHm.containsKey(tempTime) ) {
            HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get(tempTime);
            row.put("FOREIGN", row.get("FOREIGN") + " " + subtitle);
        } else {
            int intTime = Integer.parseInt(tempTime);
            int beforePos = 0;
            int nextPos = 0;

            for ( int i = 1; i <= 50; i++ ) {
                if ( subtitleHm.containsKey( Integer.toString(intTime - i) ) ) {
                    beforePos = i;
                    break;
                }
            }
            for ( int i = 1; i <= 50; i++ ) {
                if ( subtitleHm.containsKey( Integer.toString(intTime + i) ) ) {
                    nextPos = i;
                    break;
                }
            }
            if ( nextPos == 0 && beforePos == 0 ) {
                timeAl.add(Integer.parseInt(tempTime));

                HashMap<String, String> row = new HashMap<String, String>();
                row.put("HAN", "");
                row.put("FOREIGN", subtitle);

                subtitleHm.put(tempTime, row);
            } else if ( beforePos == 0 && nextPos > 0 ) {
                HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get( Integer.toString(intTime + nextPos) );
                row.put("FOREIGN", row.get("FOREIGN") + " " + subtitle);
            } else if ( nextPos == 0 && beforePos > 0 ) {
                HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get( Integer.toString(intTime - beforePos) );
                row.put("FOREIGN", row.get("FOREIGN") + " " + subtitle);
            } else if ( nextPos <  beforePos ) {
                HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get( Integer.toString(intTime + nextPos) );
                row.put("FOREIGN", row.get("FOREIGN") + " " + subtitle);
            } else {
                HashMap<String, String> row = (HashMap<String, String>)subtitleHm.get( Integer.toString(intTime - beforePos) );
                row.put("FOREIGN", row.get("FOREIGN") + " " + subtitle);
            }
        }
    }

    public static String tagAttributeValue(String tagString, String attribute) {
        //System.out.println(tagString + " : " + attribute);
        String[] attributes = tagString.substring(1, tagString.length() - 1).split(" ");
        for ( int i = 0; i < attributes.length; i++ ) {
            //System.out.println(attributes[i]);
            if ( attributes[i].indexOf("=") > 0 ) {
                String[] attr = attributes[i].split("=");
                if ( attribute.toUpperCase().equals(attr[0].toUpperCase()) ) {
                    return attr[1];
                }
            }
        }

        return "";
    }

    public static Charset detectCharset(File f, String[] charsets) {

        Charset charset = null;

        for (String charsetName : charsets) {
            charset = detectCharset(f, Charset.forName(charsetName));
            if (charset != null) {
                break;
            }
        }

        return charset;
    }

    private static Charset detectCharset(File f, Charset charset) {
        try {
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(f));

            CharsetDecoder decoder = charset.newDecoder();
            decoder.reset();

            byte[] buffer = new byte[512];
            boolean identified = false;
            while ((input.read(buffer) != -1) && (!identified)) {
                identified = identify(buffer, decoder);
            }

            input.close();

            if (identified) {
                return charset;
            } else {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    private static boolean identify(byte[] bytes, CharsetDecoder decoder) {
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }
        return true;
    }
}
