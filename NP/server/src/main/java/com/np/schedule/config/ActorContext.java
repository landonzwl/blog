package com.np.schedule.config;

public final class ActorContext {

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<Actor>();

    private ActorContext() {
    }

    public static void set(Actor actor) {
        CURRENT.set(actor);
    }

    public static Actor get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String staffId() {
        Actor actor = CURRENT.get();
        if (actor == null || actor.staffId == null) {
            return "";
        }
        return actor.staffId;
    }

    public static final class Actor {
        public final String userId;
        public final String username;
        public final String role;
        public final String staffId;
        public final String name;

        public Actor(String userId, String username, String role, String staffId, String name) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.staffId = staffId == null ? "" : staffId;
            this.name = name == null ? "" : name;
        }
    }
}
