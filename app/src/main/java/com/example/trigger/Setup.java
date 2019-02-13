package com.example.trigger;

import java.util.ArrayList;
import static com.example.trigger.Utils.*;

public interface Setup {
    public abstract int getId();
    public abstract String getName();
    public abstract String getType();
    public abstract String getSSIDs();

    // get/set a list of keys and values,
    // keys are used in SharedPreference and setup.xml
    public abstract void getAllSettings(ArrayList<Pair> pairs);
    public abstract void setAllSettings(ArrayList<Pair> pairs);
}
