package com.np.schedule.model;

public class BlockItem {

    public String id;
    public String staffId;
    public long start;
    public long end;
    public String startText;
    public String endText;
    public String reason;

    public BlockItem copy() {
        BlockItem copy = new BlockItem();
        copy.id = id;
        copy.staffId = staffId;
        copy.start = start;
        copy.end = end;
        copy.startText = startText;
        copy.endText = endText;
        copy.reason = reason;
        return copy;
    }
}
