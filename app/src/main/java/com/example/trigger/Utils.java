package com.example.trigger;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


enum Action {
    open_door,
    close_door,
    update_state
}

public class Utils {
    static <T> T[] array(T... elems) {
        return elems;
    }

    static Pair pair(String a, Object b) {
        return new Pair(a, b);
    }

    static List<String> splitCommaSeparated(String str) {
        return Arrays.asList(str.trim().split("\\s*,\\s*"));
    }

    static class Pair {
        final String key;
        Object value;

        Pair(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    public static byte[] readAllBytes(File file) {
        int size = (int) file.length();
        Log.d("Utils.readAllBytes", "length: "  + size + ", path: " + file.getPath());
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
            return bytes;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
