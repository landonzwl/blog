package com.np.schedule.model;

public class Dept {

    public String id;
    public String name;

    public Dept() {
    }

    public Dept(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Dept copy() {
        return new Dept(id, name);
    }
}
