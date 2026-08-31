package stonytark.cinemarr.core.platform;

/** Product metadata sourced from the built core JAR manifest. */
public final class ProductInfo {
    public static String version() {
        String override = System.getProperty("cinemarr.version");
        if (override != null && !override.trim().isEmpty()) return override.trim();
        Package location = ProductInfo.class.getPackage();
        String version = location == null ? null : location.getImplementationVersion();
        return version == null || version.trim().isEmpty() ? "development" : version.trim();
    }

    private ProductInfo() {}
}
