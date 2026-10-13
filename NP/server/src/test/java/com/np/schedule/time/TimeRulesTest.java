package com.np.schedule.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRulesTest {

    @Test
    void rejectsFuzzyTextAndImpossibleDates() {
        TimeRules.Stamp fuzzy = TimeRules.parseTime("上午");
        assertFalse(fuzzy.ok);
        assertTrue(fuzzy.message.contains("yyyy-MM-dd HH:mm"));
        assertFalse(TimeRules.parseTime("2026-02-31 09:00").ok);
        assertEquals("日期不存在", TimeRules.parseTime("2026-02-31 09:00").message);
        assertEquals("时间字段超出范围", TimeRules.parseTime("2026-03-02 25:00").message);
        assertFalse(TimeRules.parseClock("9:30").ok);
        assertFalse(TimeRules.parseRange("2026-03-02 12:00", "2026-03-02 12:00").ok);
    }

    @Test
    void touchingEndpointsDoNotOverlap() {
        assertFalse(TimeRules.overlaps(0, 10, 10, 20));
        assertTrue(TimeRules.overlaps(0, 11, 10, 20));
    }

    @Test
    void dateSpanIsInclusiveAndCapped() {
        assertEquals(31, TimeRules.eachDate("2026-03-01", "2026-03-31").days.size());
        assertFalse(TimeRules.eachDate("2026-03-01", "2026-04-01").ok);
        assertEquals("结束日期不能早于开始日期", TimeRules.eachDate("2026-03-05", "2026-03-03").message);
    }
}
