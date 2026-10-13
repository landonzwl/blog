package com.np.schedule.store;

public class MetaSpec {

    public final String prefix;
    public final String status;
    public final String saved;
    public final String[][] required;

    public MetaSpec(String prefix, String status, String saved, String[][] required) {
        this.prefix = prefix == null || prefix.isEmpty() ? null : prefix;
        this.status = status;
        this.saved = saved;
        this.required = required;
    }
}
