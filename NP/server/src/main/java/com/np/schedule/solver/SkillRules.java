package com.np.schedule.solver;

public final class SkillRules {

    private SkillRules() {
    }

    public static boolean matches(String personSkill, String taskSkill) {
        java.util.List<String> skills = new java.util.ArrayList<String>();
        if (personSkill != null) skills.add(personSkill);
        return matches(skills, taskSkill);
    }

    public static boolean matches(java.util.List<String> personSkills, String taskSkill) {
        return matches(personSkills, taskSkill, null);
    }

    public static boolean matches(java.util.List<String> personSkills, String taskSkill, java.util.Map<String, String> book) {
        String raw = taskSkill == null ? "" : taskSkill.trim();
        if (raw.indexOf(',') >= 0 || raw.indexOf('，') >= 0) {
            boolean asked = false;
            for (String part : raw.split("[,，]")) {
                if (part.trim().isEmpty()) continue;
                asked = true;
                if (matches(personSkills, part.trim(), book)) return true;
            }
            return !asked;
        }
        String task = lookup(taskSkill, book);
        if (task.isEmpty() || "通用".equals(task)) return true;
        if (personSkills == null) return false;
        for (String skill : personSkills) {
            String name = lookup(skill, book);
            if ("全班".equals(name) || task.equals(name)) return true;
        }
        return false;
    }

    /**
     * 负载相同时，精确技能排在「全班」前面，避免专岗任务先抢走全能的人。
     */
    public static int rank(String personSkill, String taskSkill) {
        java.util.List<String> skills = new java.util.ArrayList<String>();
        if (personSkill != null) skills.add(personSkill);
        return rank(skills, taskSkill);
    }

    public static int rank(java.util.List<String> personSkills, String taskSkill) {
        return rank(personSkills, taskSkill, null);
    }

    public static int rank(java.util.List<String> personSkills, String taskSkill, java.util.Map<String, String> book) {
        String raw = taskSkill == null ? "" : taskSkill.trim();
        if (raw.indexOf(',') >= 0 || raw.indexOf('，') >= 0) {
            int best = 1;
            boolean asked = false;
            for (String part : raw.split("[,，]")) {
                if (part.trim().isEmpty()) continue;
                asked = true;
                best = Math.min(best, rank(personSkills, part.trim(), book));
            }
            return asked ? best : 0;
        }
        String task = lookup(taskSkill, book);
        if (task.isEmpty() || "通用".equals(task)) return 0;
        if (personSkills != null) {
            for (String skill : personSkills) {
                if (task.equals(lookup(skill, book))) return 0;
            }
        }
        return 1;
    }

    private static String lookup(String token, java.util.Map<String, String> book) {
        if (token == null) return "";
        String value = token.trim();
        if (book != null && book.containsKey(value)) return book.get(value);
        return value;
    }
}
