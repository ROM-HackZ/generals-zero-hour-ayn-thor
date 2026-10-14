package com.generalsx.zerohour;
import android.graphics.*;
import android.util.LruCache;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;

// GeneralsX @feature Codex 14/10/2026 Shared military panels, measured text and cached retail artwork.
final class ThorUi {
    static final int BACKGROUND=0xff0c1219, PANEL=0xff17232e, LINE=0xff344552;
    static final int TEXT=0xffedf2f5, MUTED=0xffa5b4c0, FOCUS=0xffffce76;
    final LruCache<String,Bitmap> art = new LruCache<String,Bitmap>(16*1024*1024) {
        protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    final HashSet<String> missing=new HashSet<>();
    int generation=-1, fetches, accent=0xff75b9e8;
    void begin(JSONObject state) {
        int next=state.optInt("iconGeneration");
        if(next!=generation) { generation=next; art.evictAll(); missing.clear(); }
        String faction=state.optString("faction").toLowerCase(Locale.ROOT);
        accent=faction.contains("china")?0xffdd8178:faction.contains("gla")?0xff87b987:0xff75b9e8;
        fetches=0;
    }
    void fetch(String key) {
        if(key.isEmpty() || art.get(key)!=null || missing.contains(key) || fetches>=8) return;
        ++fetches; int[] pixels=ThorBridge.nativeIcon(key);
        if(pixels==null) return;
        if(pixels.length!=128*128) { if(missing.size()<256) missing.add(key); return; }
        art.put(key,Bitmap.createBitmap(pixels,128,128,Bitmap.Config.ARGB_8888));
    }
    void panel(Canvas c,Paint p,RectF r,boolean focused) {
        p.setStyle(Paint.Style.FILL); p.setAlpha(255); p.setColor(focused?0xff293948:PANEL); c.drawRoundRect(r,12,12,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(focused?3:1); p.setColor(focused?FOCUS:LINE); c.drawRoundRect(r,12,12,p); p.setStyle(Paint.Style.FILL);
    }
    void icon(Canvas c,Paint p,String key,RectF r,boolean enabled) {
        Bitmap bitmap=art.get(key); p.setAlpha(enabled?255:100); p.setFilterBitmap(true);
        if(bitmap!=null) c.drawBitmap(bitmap,null,r,p);
        else {
            p.setColor(enabled?accent:MUTED); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            RectF mark=new RectF(r.centerX()-r.width()*.22f,r.centerY()-r.height()*.22f,r.centerX()+r.width()*.22f,r.centerY()+r.height()*.22f);
            c.drawRoundRect(mark,5,5,p); c.drawLine(mark.left,mark.centerY(),mark.right,mark.centerY(),p); c.drawLine(mark.centerX(),mark.top,mark.centerX(),mark.bottom,p); p.setStyle(Paint.Style.FILL);
        }
        p.setAlpha(255);
    }
    static void text(Canvas c,Paint p,String text,float x,float y,float size,int color,boolean bold) {
        p.setStyle(Paint.Style.FILL); p.setAlpha(255); p.setColor(color); p.setTextSize(size); p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT); p.setTextAlign(Paint.Align.LEFT); c.drawText(text,x,y,p);
    }
    static void center(Canvas c,Paint p,String text,float x,float y,float size,int color,boolean bold) {
        p.setTextSize(size); p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT); text(c,p,text,x-p.measureText(text)/2,y,size,color,bold);
    }
    static void wrap(Canvas c,Paint p,String value,RectF bounds,float size,int color,boolean bold,int maxLines,boolean centered) {
        p.setTextSize(size); p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        String rest=value.replace('\r',' ').trim(); float y=bounds.top+size;
        for(int line=0;!rest.isEmpty() && line<maxLines && y<=bounds.bottom;++line) {
            int count=p.breakText(rest,true,bounds.width(),null); if(count==0) break;
            int newline=rest.indexOf('\n');
            if(newline>=0 && newline<count) count=newline;
            else if(count<rest.length()) { int space=rest.lastIndexOf(' ',count); if(space>0) count=space; }
            String part=rest.substring(0,count).trim(); rest=rest.substring(count).trim();
            if(line==maxLines-1 && !rest.isEmpty()) { while(!part.isEmpty() && p.measureText(part+"…")>bounds.width()) part=part.substring(0,part.length()-1); part+="…"; }
            if(centered) center(c,p,part,bounds.centerX(),y,size,color,bold); else text(c,p,part,bounds.left,y,size,color,bold);
            y+=size*1.25f;
        }
    }
    static String name(JSONObject row) { String s=row.optString("label"); return s.equals("Generals Experience Menu")?"General's Powers":s; }
    static java.util.ArrayList<String> lines(Paint p,String value,float width,float size) {
        p.setTextSize(size); p.setTypeface(Typeface.DEFAULT);
        java.util.ArrayList<String> result=new java.util.ArrayList<>();
        for(String paragraph:value.replace('\r',' ').split("\n",-1)) {
            String rest=paragraph.trim();
            if(rest.isEmpty()) { result.add(""); continue; }
            while(!rest.isEmpty()) {
                int count=p.breakText(rest,true,width,null); if(count==0) break;
                if(count<rest.length()) { int space=rest.lastIndexOf(' ',count); if(space>0) count=space; }
                result.add(rest.substring(0,count).trim()); rest=rest.substring(count).trim();
            }
        }
        return result;
    }
    static String status(JSONObject row,JSONObject state) {
        if(row.optBoolean("queue")) return "A Cancel production";
        if(row.optBoolean("passengerSlot")) return row.has("occupant")?"A Exit passenger":"Empty";
        if(row.has("checked")) return row.optBoolean("checked")?"On":"Off";
        if(row.optBoolean("active")) return "Active";
        if(row.optBoolean("learned")) return "Learned";
        if(row.optBoolean("science") && !row.optBoolean("enabled") && row.optInt("scienceCost")>state.optInt("sciencePoints")) return "Insufficient points";
        if(row.has("recharge") && row.optDouble("recharge",1)<.999) return "Recharging · "+Math.round(row.optDouble("recharge")*100)+"%";
        if(!row.optBoolean("enabled")) return row.optInt("cost")>state.optInt("money")?"Insufficient funds":"Unavailable";
        return "Ready";
    }
    // GeneralsX @feature Codex 14/10/2026 The deck exposes toggle state rather than a generic ready label.
    static String badge(JSONObject row) {
        if(row.optBoolean("passengerSlot") && !row.has("occupant")) return "EMPTY";
        if(row.has("checked")) return row.optBoolean("checked")?"ON":"OFF";
        if(row.optBoolean("learned")) return "LEARNED";
        if(row.optBoolean("active")) return "ACTIVE";
        int rank=row.optInt("veterancy"); if(rank>=1 && rank<=3) return new String[]{"","VETERAN","ELITE","HEROIC"}[rank];
        if(row.has("recharge") && row.optDouble("recharge")<.999) return Math.round(row.optDouble("recharge")*100)+"%";
        return row.optBoolean("enabled")?"":"LOCKED";
    }
    static String cost(JSONObject row) { if(row.has("sliderValue")) return String.valueOf(row.optInt("sliderValue")); int points=row.optInt("scienceCost"); return points>0?points+(points==1?" point":" points"):row.optInt("cost")>0?"$"+row.optInt("cost"):""; }
    static void progress(Canvas c,Paint p,RectF r,float value,int color) {
        p.setColor(0xff091018); c.drawRoundRect(r,3,3,p);
        float amount=Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;
        if(amount>0) { p.setColor(color); c.drawRoundRect(new RectF(r.left,r.top,r.left+r.width()*amount,r.bottom),3,3,p); }
    }
}
