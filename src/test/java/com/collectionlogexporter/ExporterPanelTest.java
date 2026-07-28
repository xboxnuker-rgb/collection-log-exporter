package com.collectionlogexporter;

import java.util.Arrays;
import java.util.Collections;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ExporterPanelTest
{
	@Test
	public void unlocksAfterSnapshotWhileChecklistTracksOptionalKcCoverage() throws Exception
	{
		ExporterPanel[] holder = new ExporterPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new ExporterPanel(
			() -> { },
			(path, options) -> { },
			() -> EstimateMode.AUTO));
		ExporterPanel panel = holder[0];
		assertNotSame(panel, panel.getWrappedPanel());
		assertFalse(panel.isExportUnlocked());

		panel.setPageChecklist(Arrays.asList(
			new PageDefinition("Bosses", "First boss", new int[]{1}),
			new PageDefinition("Other", "Counterless page", new int[]{2})),
			Collections.emptySet());
		panel.setReady(0, -1, -1, 2, 2, 0, 2);
		flushEdt();
		assertTrue(panel.isExportUnlocked());

		panel.markPageVisited("First boss");
		panel.setReady(0, -1, -1, 2, 2, 1, 2);
		flushEdt();
		assertTrue(panel.isPageChecked("First boss"));
		assertTrue(panel.isExportUnlocked());
		assertTrue(panel.pageListPosition("Counterless page")
			< panel.pageListPosition("First boss"));

		panel.markPageVisited("Counterless page");
		panel.setReady(0, -1, -1, 2, 2, 2, 2);
		flushEdt();
		assertTrue(panel.isPageChecked("Counterless page"));
		assertTrue(panel.isExportUnlocked());
	}

	@Test
	public void officialProgressDrivesTheGlobalMissingTotal() throws Exception
	{
		ExporterPanel[] holder = new ExporterPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new ExporterPanel(
			() -> { },
			(path, options) -> { },
			() -> EstimateMode.AUTO));

		holder[0].setReady(914, 910, 1706, 97, 808, 81, 123);
		flushEdt();

		assertTrue(holder[0].countText().contains("796 unique missing slots (official)"));
		assertTrue(holder[0].countText().contains("808 currently identified by item scan"));
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}
}
