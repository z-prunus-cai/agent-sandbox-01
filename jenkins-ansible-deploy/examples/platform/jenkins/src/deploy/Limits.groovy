package deploy

/** Bounds on everything a build accepts. */
class Limits {
    public static final int MAX_PLAN_CHARACTERS = 20000
    public static final int MAX_PLAN_ENTRIES = 50
    public static final int MAX_STRING_PARAMETER_CHARACTERS = 1000
    public static final int MAX_ID_CHARACTERS = 64
    public static final int MAX_TIMEOUT_MINUTES = 240
}
