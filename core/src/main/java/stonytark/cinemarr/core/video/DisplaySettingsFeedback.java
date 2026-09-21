package stonytark.cinemarr.core.video;

/** Loader-independent routing of server errors to an open settings page. */
public interface DisplaySettingsFeedback {
    void showError(String message);
}
