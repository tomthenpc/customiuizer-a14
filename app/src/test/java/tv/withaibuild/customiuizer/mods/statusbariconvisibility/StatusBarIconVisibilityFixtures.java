package tv.withaibuild.customiuizer.mods.statusbariconvisibility;

/**
 * Java fixtures for the StatusBarIconVisibility Architecture C component tests.
 *
 * Java is used for the fixture classes because it allows field shadowing and
 * primitive/wrapper distinctions without Kotlin property-override complications.
 */
@SuppressWarnings("unused")
public final class StatusBarIconVisibilityFixtures {

    private StatusBarIconVisibilityFixtures() {}

    /** Base mobile-icon state with inherited fields. */
    public static class BaseMobileIconState {
        public boolean wifiAvailable = false;
        public int subId = 0;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Concrete mobile-icon state where all fields are declared on the subclass. */
    public static class DeclaredMobileIconState {
        public boolean wifiAvailable = false;
        public int subId = 0;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Mobile-icon state where all fields are inherited from [BaseMobileIconState]. */
    public static class InheritedMobileIconState extends BaseMobileIconState {}

    /** Mobile-icon state with a non-primitive Boolean wifiAvailable (must be rejected). */
    public static class BooleanWifiMobileIconState {
        public Boolean wifiAvailable = false;
        public int subId = 0;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Mobile-icon state with a long subId (must be rejected for FAST). */
    public static class LongSubIdMobileIconState {
        public boolean wifiAvailable = false;
        public long subId = 0L;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Mobile-icon state missing the wifiAvailable field. */
    public static class MissingWifiMobileIconState {
        public int subId = 0;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Mobile-icon state missing the subId field. */
    public static class MissingSubIdMobileIconState {
        public boolean wifiAvailable = false;
        public boolean visible = true;
        public boolean roaming = false;
        public boolean volte = false;
        public boolean speechHd = false;
    }

    /** Mobile-icon state where one boolean write field is the wrong type. */
    public static class WrongTypeVolteMobileIconState {
        public boolean wifiAvailable = false;
        public int subId = 0;
        public boolean visible = true;
        public boolean roaming = false;
        public int volte = 0;
        public boolean speechHd = false;
    }

    /** Base class for the StatusBarMobileView root (used for inherited mState and superclass mismatch). */
    public static class BaseStatusBarMobileView {
        public Object mState = null;
    }

    /** Concrete StatusBarMobileView root. Its mState is inherited from [BaseStatusBarMobileView]. */
    public static class StatusBarMobileView extends BaseStatusBarMobileView {
        public void applyMobileState(DeclaredMobileIconState state) {}
        public void updateState(DeclaredMobileIconState state) {}
    }

    /** Subclass of [StatusBarMobileView] that shadows mState. */
    public static class SubStatusBarMobileView extends StatusBarMobileView {
        public Object mState = null;
    }

    /** StatusBarMobileView whose hook methods take an opaque Object parameter. */
    public static class ObjectParamStatusBarMobileView {
        public Object mState = null;
        public void applyMobileState(Object state) {}
        public void updateState(Object state) {}
    }

    /** StatusBarMobileView whose mState type is a concrete state class (fallback path). */
    public static class MStateTypedStatusBarMobileView {
        public DeclaredMobileIconState mState = null;
    }
}
