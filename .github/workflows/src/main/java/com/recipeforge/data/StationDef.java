package com.recipeforge.data;

public class StationDef {
    public String name = "";
    public int inputs = 1;
    public String template = "";
    public String param = "";

    public StationDef() {}

    public StationDef(String name, int inputs, String template, String param) {
        this.name = name;
        this.inputs = inputs;
        this.template = template;
        this.param = param;
    }
}
