package stonytark.cinemarr.core.library;

/** A server-filtered Plex audio or subtitle stream safe to expose to clients. */
public final class VideoStreamOption {
    public enum Kind { AUDIO, SUBTITLE }
    private final Kind kind; private final int id; private final String label,language,codec; private final boolean selected;
    public VideoStreamOption(Kind kind,int id,String label,String language,String codec,boolean selected){if(kind==null||id<1)throw new IllegalArgumentException("Invalid stream option");this.kind=kind;this.id=id;this.label=safe(label);this.language=safe(language);this.codec=safe(codec);this.selected=selected;}
    public Kind kind(){return kind;}public int id(){return id;}public String label(){return label;}public String language(){return language;}public String codec(){return codec;}public boolean selected(){return selected;}
    private static String safe(String value){return value==null?"":value;}
}
