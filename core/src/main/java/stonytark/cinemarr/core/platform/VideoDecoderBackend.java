package stonytark.cinemarr.core.platform;

import java.util.Locale;

/**
 * Local client video decoder selection.
 *
 * <p>Only {@code software}, {@code auto}, and {@code vaapi} are accepted from
 * user configuration. The remaining values describe backends selected
 * internally by {@code auto} and are retained for diagnostics and benchmark
 * evidence; they are intentionally not explicit user options.</p>
 */
public enum VideoDecoderBackend {
    SOFTWARE("software"),
    AUTO("auto"),
    VAAPI("vaapi"),
    QSV("qsv"),
    CUDA("cuda"),
    D3D11VA("d3d11va"),
    DXVA2("dxva2");

    private final String configValue;

    VideoDecoderBackend(String configValue) { this.configValue = configValue; }

    public String configValue() { return configValue; }

    public VideoDecoderBackend next() {
        switch (this) {
            case SOFTWARE: return AUTO;
            case AUTO: return VAAPI;
            default: return SOFTWARE;
        }
    }

    public static VideoDecoderBackend parse(String value) {
        VideoDecoderBackend backend = parseInternal(value);
        return backend == SOFTWARE || backend == AUTO || backend == VAAPI ? backend : SOFTWARE;
    }

    /** Parses benchmark/diagnostic backend names; never use this for client configuration. */
    public static VideoDecoderBackend parseInternal(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (VideoDecoderBackend backend : values()) {
                if (backend.configValue.equals(normalized) || backend.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return backend;
                }
            }
        }
        return SOFTWARE;
    }
}
