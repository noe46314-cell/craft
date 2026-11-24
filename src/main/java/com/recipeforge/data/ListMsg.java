package com.recipeforge.data;

import java.util.ArrayList;
import java.util.List;

public class ListMsg {
    public boolean can = false;
    public String msg = "";
    public String savedId = "";
    public List<Entry> entries = new ArrayList<Entry>();
    public List<StationDef> stations = new ArrayList<StationDef>();
}
