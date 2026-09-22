package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySettingsPageTest {
    private VideoPackets.SessionState state(TvDisplaySettings display, boolean control) {
        return new VideoPackets.SessionState(UUID.randomUUID(),1,UUID.randomUUID(),0,VideoPackets.SessionStatus.IDLE,
                null,0,0,false,display.layout(),17,11,new byte[0],stonytark.cinemarr.core.screen.ScreenFacing.NORTH,0,0,0,java.util.Collections.emptyList(),-1,-1,0,control,"").withDisplay(display,0,0);
    }
    @Test void everyPresetAndCustomAreReachableAndOnlyCustomEnablesFields() {
        DisplaySettingsPage page=new DisplaySettingsPage(state(TvDisplaySettings.defaults(PresentationMode.FIT),true));
        assertFalse(page.customEditable());
        for(QuickTvPreset preset:QuickTvPreset.values()) {
            page.cycleResolution();assertEquals("Quality: "+preset.id(),page.resolutionLabel());assertFalse(page.customEditable());
        }
        page.cycleResolution();assertTrue(page.customEditable());page.dimensions("426","240");
        VideoPackets.SessionCommand command=page.apply(1,100);
        assertEquals(ResolutionChoice.custom(426,240),command.displaySettings().resolution());assertEquals(0,command.displaySettings().revision());
        assertTrue(page.pending());assertFalse(page.editable());assertNull(page.apply(1,101));
        TvDisplaySettings accepted=TvDisplaySettings.defaults(PresentationMode.FIT).apply(0,command.displaySettings().layout(),command.displaySettings().mapping(),command.displaySettings().resolution());
        page.update(state(accepted,true),102);assertFalse(page.pending());assertEquals("Saved",page.message());assertTrue(page.customEditable());
    }
    @Test void timeoutServerFailureAndConflictingAckKeepTheDraft() {
        TvDisplaySettings original=TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsPage page=new DisplaySettingsPage(state(original,true));page.cycleLayout();page.apply(1,100);
        page.update(state(original,true),10100);assertFalse(page.pending());assertTrue(page.message().contains("acknowledgement"));assertEquals("Layout: FILL",page.layoutLabel());
        page.apply(1,11000);page.fail("Denied");assertEquals("Denied",page.message());
        page.apply(1,12000);page.update(state(original.apply(0,PresentationMode.STRETCH,PixelMapping.DETAILED,ResolutionChoice.AUTO),true),12001);
        assertTrue(page.message().contains("Settings changed"));assertEquals("Layout: FILL",page.layoutLabel());
    }
    @Test void readOnlyAndQuickRestrictionsApplyBeforeSending() {
        TvDisplaySettings quick=new TvDisplaySettings(TvDisplaySettings.Origin.QUICK,PresentationMode.FIT,PixelMapping.DETAILED,ResolutionChoice.preset("144p"),2);
        DisplaySettingsPage page=new DisplaySettingsPage(state(quick,false));page.cycleLayout();assertEquals("Layout: FIT",page.layoutLabel());assertNull(page.apply(1,0));
        page.update(state(quick,true),1);assertTrue(page.editable());assertFalse(page.qualityEditable());page.cycleResolution();page.cycleMapping();page.cycleLayout();
        assertEquals(PixelMapping.DETAILED,page.apply(1,2).displaySettings().mapping());
    }
    @Test void invalidCustomInputSurvivesFeedbackAndDoesNotSend() {
        DisplaySettingsPage page=new DisplaySettingsPage(state(TvDisplaySettings.defaults(PresentationMode.FIT),true));
        for(int i=0;i<9;i++)page.cycleResolution();
        page.dimensions("oops","240");assertNull(page.apply(1,0));assertEquals("oops",page.width());assertFalse(page.pending());
        page.dimensions("8192","8192");assertNull(page.apply(1,0));assertTrue(page.customEditable());
    }
    @Test void pendingApplyIsClearedWhenTvDisappearsOrPermissionIsRevoked() {
        TvDisplaySettings original=TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsPage removed=new DisplaySettingsPage(state(original,true));
        assertNotNull(removed.apply(1,0));removed.update(null,1);
        assertFalse(removed.pending());assertFalse(removed.editable());
        DisplaySettingsPage revoked=new DisplaySettingsPage(state(original,true));
        assertNotNull(revoked.apply(1,0));revoked.update(state(original,false),1);
        assertFalse(revoked.pending());assertFalse(revoked.editable());
    }
    @Test void staleDraftCannotBeSentAfterAnotherViewerChangesSettings() {
        TvDisplaySettings original=TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsPage page=new DisplaySettingsPage(state(original,true));
        TvDisplaySettings changed=original.apply(0,PresentationMode.FILL,PixelMapping.DETAILED,ResolutionChoice.AUTO);
        page.update(state(changed,true),1);
        assertNull(page.apply(1,2));assertFalse(page.pending());
        page.reload(state(changed,true));assertNotNull(page.apply(1,3));
    }
}
