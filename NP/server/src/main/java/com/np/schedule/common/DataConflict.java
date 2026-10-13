package com.np.schedule.common;

public class DataConflict extends RuntimeException {

    public DataConflict() {
        super("数据已被他人修改，请刷新后重试");
    }
}
