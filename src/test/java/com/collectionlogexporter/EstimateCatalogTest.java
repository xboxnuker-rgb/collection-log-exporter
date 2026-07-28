package com.collectionlogexporter;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EstimateCatalogTest
{
	@Test
	public void loadsBundledRatesAndChoosesAccountProfile() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		assertTrue(catalog.supportedItemCount() > 1_000);

		ItemEstimate main = catalog.estimate(13262, "Abyssal orphan", false);
		ItemEstimate iron = catalog.estimate(13262, "Abyssal orphan", true);
		assertTrue(main.isKnown());
		assertTrue(iron.isKnown());
		assertEquals("Killing abyssal sire (on task)", main.getActivity());
		assertTrue(iron.getEstimatedHours() > main.getEstimatedHours());
	}

	@Test
	public void fallsBackToItemNameAndLeavesUnknownsBlank() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		assertTrue(catalog.estimate(-1, "Abyssal orphan", false).isKnown());
		assertFalse(catalog.estimate(-1, "Definitely not a real item", false).isKnown());
	}

	@Test
	public void incompleteEstimateCoverageDoesNotInventPageEta() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		ExportData data = new ExportDataBuilder(catalog).build(
			"Player",
			Collections.singletonList(new PageDefinition("Other", "Test", new int[]{13262, -999})),
			Collections.emptyMap(),
			id -> id == 13262 ? "Abyssal orphan" : "Unknown",
			page -> "",
			(page, id) -> "",
			false,
			SortMode.CLOSEST,
			-1,
			-1);

		assertEquals(1, data.getPages().size());
		assertFalse(Double.isFinite(data.getPages().get(0).getEstimatedHours()));
		assertEquals(2, data.getRows().size());
	}

	@Test
	public void cacheVariationNameFallbackCountsConfirmedOwnership() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		ExportData data = new ExportDataBuilder(catalog).build(
			"Player",
			Collections.singletonList(new PageDefinition("Bosses", "Abyssal Sire", new int[]{13262})),
			Collections.singletonMap(-1, 1),
			id -> "Abyssal orphan",
			page -> "",
			(page, id) -> "",
			false,
			SortMode.CLOSEST,
			910,
			1706);

		assertTrue(data.getPages().isEmpty());
		assertTrue(data.getRows().isEmpty());
		assertEquals(1, data.getScannedObtained());
		assertEquals(1, data.getScannedTotal());
		assertEquals(910, data.getOfficialObtained());
		assertEquals(1706, data.getOfficialTotal());
	}

	@Test
	public void sharedSlotsAreEmittedOnlyOnceAcrossPages() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		ExportData data = new ExportDataBuilder(catalog).build(
			"Player",
			java.util.Arrays.asList(
				new PageDefinition("Clues", "Hard Treasure Trails", new int[]{13262}),
				new PageDefinition("Clues", "Elite Treasure Trails", new int[]{13262, -999})),
			Collections.emptyMap(),
			id -> id == 13262 ? "Abyssal orphan" : "Unknown",
			page -> "",
			(page, id) -> "",
			false,
			SortMode.LOG_ORDER,
			0,
			2);

		assertEquals(2, data.getPages().size());
		assertEquals(2, data.getRows().size());
		assertEquals(2, data.getScannedTotal());
	}

	@Test
	public void creditsCompletedKcAndMarksItemsOverRateAsAnytime()
	{
		ItemEstimate estimate = new ItemEstimate("Boss", 20.0, 100.0, 5.0, "Standard");
		assertEquals(50.0, estimate.effectiveAttemptsRemaining(50), 0.001);
		assertEquals(2.5, estimate.effectiveHours(50), 0.001);
		assertTrue(estimate.isAnytime(120));
		assertEquals(20.0, estimate.kcOverRate(120), 0.001);
		assertEquals(0.0, estimate.effectiveHours(120), 0.001);
		assertEquals(1457, AttemptCounter.fromText("Boss kills: 1,457"));
	}

	@Test
	public void scrollCasesUseTheirOwnTierCountAndCumulativeMilestone() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		int minor = ItemID.SCROLL_CASE_BEGINNER_MINOR;
		int major = ItemID.SCROLL_CASE_BEGINNER_MAJOR;
		Map<Integer, String> names = new HashMap<>();
		names.put(minor, "Minor beginner scroll case");
		names.put(major, "Major beginner scroll case");

		ExportData data = new ExportDataBuilder(catalog).build(
			"Player",
			Collections.singletonList(
				new PageDefinition("Clues", "Scroll Cases", new int[]{minor, major})),
			Collections.emptyMap(),
			names::get,
			page -> "",
			(page, id) -> "Jagex hiscores - Beginner clues completed: 25",
			false,
			SortMode.CLOSEST,
			0,
			2);

		assertEquals(2, data.getRows().size());
		ExportRow minorRow = data.getRows().stream()
			.filter(row -> row.getItemId() == minor)
			.findFirst()
			.orElseThrow(AssertionError::new);
		ExportRow majorRow = data.getRows().stream()
			.filter(row -> row.getItemId() == major)
			.findFirst()
			.orElseThrow(AssertionError::new);

		assertEquals(25, minorRow.getCompletedAttempts());
		assertEquals(25, majorRow.getCompletedAttempts());
		assertEquals(50.0, minorRow.getExpectedAdditionalAttempts(), 0.001);
		assertEquals(100.0, majorRow.getExpectedAdditionalAttempts(), 0.001);
		assertEquals(25.0, minorRow.getAttemptsToRate(), 0.001);
		assertEquals(75.0, majorRow.getAttemptsToRate(), 0.001);
		assertEquals(
			majorRow.getEstimatedItemHours(),
			data.getPages().get(0).getEstimatedHours(),
			0.001);
	}

	@Test
	public void scrollCaseItemsCanUseDifferentTierCounters() throws Exception
	{
		EstimateCatalog catalog = EstimateCatalog.load(new Gson());
		int beginner = ItemID.SCROLL_CASE_BEGINNER_MINOR;
		int medium = ItemID.SCROLL_CASE_MEDIUM_MAJOR;

		ExportData data = new ExportDataBuilder(catalog).build(
			"Player",
			Collections.singletonList(
				new PageDefinition("Clues", "Scroll Cases", new int[]{beginner, medium})),
			Collections.emptyMap(),
			id -> id == beginner
				? "Minor beginner scroll case"
				: "Major medium scroll case",
			page -> "",
			(page, id) -> id == beginner
				? "Beginner clues completed: 60"
				: "Medium clues completed: 120",
			false,
			SortMode.CLOSEST,
			0,
			2);

		ExportRow beginnerRow = data.getRows().stream()
			.filter(row -> row.getItemId() == beginner)
			.findFirst()
			.orElseThrow(AssertionError::new);
		ExportRow mediumRow = data.getRows().stream()
			.filter(row -> row.getItemId() == medium)
			.findFirst()
			.orElseThrow(AssertionError::new);

		assertTrue(beginnerRow.isItemAnytime());
		assertEquals(10.0, beginnerRow.getKcOverRate(), 0.001);
		assertEquals(120, mediumRow.getCompletedAttempts());
		assertEquals(250.0, mediumRow.getExpectedAdditionalAttempts(), 0.001);
		assertEquals(130.0, mediumRow.getAttemptsToRate(), 0.001);
	}
}
