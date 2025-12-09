package applane.knob;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    public static String filename = "preferences";
    public static boolean getBool(Context ctx, String name, boolean defValue)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        return prefs.getBoolean(name, defValue);
    }
    public static void setBool(Context ctx, String name, boolean value)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(name, value);
        edit.commit();
    }
    public static int getInt(Context ctx, String name, int defValue)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        return prefs.getInt(name, defValue);
    }
    public static void setInt(Context ctx, String name, int value)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(name, value);
        edit.commit();
    }
    public static long getLong(Context ctx, String name, long defValue)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        return prefs.getLong(name, defValue);
    }
    public static void setLong(Context ctx, String name, long value)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(name, value);
        edit.commit();
    }
    public static String getStr(Context ctx, String name, String defValue)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        return prefs.getString(name, defValue);
    }
    public static void setStr(Context ctx, String name, String value)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(Prefs.filename, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(name, value);
        edit.commit();
    }
}
