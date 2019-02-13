package com.example.trigger;

import java.util.ArrayList;
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
}
