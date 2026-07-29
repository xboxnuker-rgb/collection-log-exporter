package com.collectionlogexporter;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SpreadsheetExporterTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void writesValidXlsxPackageWithExpectedSheets() throws Exception
	{
		Path target = temporaryFolder.newFile("log.xlsx").toPath();
		SpreadsheetExporter.write(
			target,
			sampleData(),
			new ExportOptions(ExportFormat.XLSX, DetailLevel.BOTH, SortMode.CLOSEST, EstimateMode.MAIN));

		try (ZipFile zip = new ZipFile(target.toFile()))
		{
			Set<String> names = new HashSet<>();
			zip.stream().map(ZipEntry::getName).forEach(names::add);
			assertTrue(names.contains("[Content_Types].xml"));
			assertTrue(names.contains("xl/workbook.xml"));
			assertTrue(names.contains("xl/styles.xml"));
			assertTrue(names.contains("xl/worksheets/sheet1.xml"));
			assertTrue(names.contains("xl/worksheets/sheet2.xml"));
			assertTrue(names.contains("xl/worksheets/sheet3.xml"));
			assertTrue(names.contains("xl/tables/table1.xml"));
			assertTrue(names.contains("xl/tables/table2.xml"));
			assertTrue(names.contains("xl/worksheets/_rels/sheet1.xml.rels"));
			assertTrue(names.contains("xl/worksheets/_rels/sheet3.xml.rels"));
			assertTrue(names.contains("xl/drawings/drawing1.xml"));
			assertTrue(names.contains("xl/drawings/_rels/drawing1.xml.rels"));
			assertTrue(names.contains("xl/media/image1.png"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet1.xml").contains("Boss kills: 420"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet1.xml").contains("Anytime"));
			assertTrue(readEntry(zip, "xl/tables/table1.xml").contains("ref=\"A1:L2\""));
			assertTrue(readEntry(zip, "xl/tables/table1.xml").contains("<autoFilter ref=\"A1:L2\"/>"));
			assertTrue(!readEntry(zip, "xl/tables/table1.xml").contains("totalsRowCount"));
			assertTrue(readEntry(zip, "xl/tables/table1.xml").contains("TableStyleMedium4"));
			String itemTable = readEntry(zip, "xl/tables/table2.xml");
			assertTrue(itemTable.indexOf("name=\"Category\"") < itemTable.indexOf("name=\"Page\""));
			assertTrue(itemTable.indexOf("name=\"Estimated item hours\"")
				< itemTable.indexOf("name=\"Suggested activity\""));
			assertTrue(itemTable.indexOf("name=\"Item ID\"")
				> itemTable.indexOf("name=\"Estimate method\""));
			assertTrue(itemTable.contains("name=\"Page priority\""));
			assertTrue(itemTable.contains("id=\"1\" name=\"Category\""));
			assertTrue(!itemTable.contains("totalsRowLabel"));
			assertTrue(itemTable.contains("headerRowCount=\"1\""));
			assertTrue(readEntry(zip, "xl/styles.xml").contains("FFEAF2EC"));
			assertTrue(readEntry(zip, "xl/styles.xml").contains("[&lt;10]0.###;0"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet2.xml")
				.contains("<c r=\"F2\" s=\"7\"><v>0.8</v></c>"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("Remaining items column"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("Thank you for using it"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("Support GSVS UK ACM on Patreon"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("<hyperlink ref=\"A3\" r:id=\"rId2\"/>"));
			assertTrue(readEntry(zip, "xl/worksheets/_rels/sheet3.xml.rels")
				.contains("https://www.patreon.com/GSVS_UK_ACM/posts/buy-us-virtual-165207029"));
			assertTrue(readEntry(zip, "xl/worksheets/_rels/sheet3.xml.rels")
				.contains("TargetMode=\"External\""));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("<drawing r:id=\"rId1\"/>"));
			assertTrue(readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("<mergeCell ref=\"A1:B1\"/>"));
			assertTrue(readEntry(zip, "xl/drawings/drawing1.xml")
				.contains("Collection Log Exporter logo"));
			assertTrue(!readEntry(zip, "xl/worksheets/sheet3.xml")
				.contains("<autoFilter"));
		}
		Path validationCopy = Path.of("build", "validation", "collection-log-sample.xlsx");
		Files.createDirectories(validationCopy.getParent());
		Files.copy(target, validationCopy, StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	public void writesOdsWithUncompressedMimetypeFirst() throws Exception
	{
		Path target = temporaryFolder.newFile("log.ods").toPath();
		SpreadsheetExporter.write(
			target,
			sampleData(),
			new ExportOptions(ExportFormat.ODS, DetailLevel.BOTH, SortMode.CLOSEST, EstimateMode.MAIN));

		try (ZipFile zip = new ZipFile(target.toFile()))
		{
			ZipEntry mimetype = zip.getEntry("mimetype");
			assertNotNull(mimetype);
			assertEquals(ZipEntry.STORED, mimetype.getMethod());
			assertNotNull(zip.getEntry("content.xml"));
			assertNotNull(zip.getEntry("META-INF/manifest.xml"));
			assertNotNull(zip.getEntry("Pictures/logo.png"));
			String content = readEntry(zip, "content.xml");
			assertTrue(content.contains("Boss kills: 420"));
			assertTrue(content.contains("shadeWhole"));
			assertTrue(content.contains("adaptiveCell"));
			assertTrue(content.contains("Remaining items column"));
			assertTrue(content.contains("Pictures/logo.png"));
			assertTrue(content.contains("Thank you for using it"));
			assertTrue(content.contains("Support GSVS UK ACM on Patreon"));
			assertTrue(content.contains(
				"xlink:href=\"https://www.patreon.com/GSVS_UK_ACM/posts/buy-us-virtual-165207029\""));
			assertTrue(content.contains("table:number-columns-spanned=\"2\""));
		}
	}

	@Test
	public void csvEscapesQuotesAndStartsWithUtf8Bom() throws Exception
	{
		Path target = temporaryFolder.newFile("log.csv").toPath();
		SpreadsheetExporter.write(
			target,
			sampleData(),
			new ExportOptions(ExportFormat.CSV, DetailLevel.BOTH, SortMode.CLOSEST, EstimateMode.MAIN));

		byte[] bytes = Files.readAllBytes(target);
		assertEquals((byte) 0xEF, bytes[0]);
		assertEquals((byte) 0xBB, bytes[1]);
		assertEquals((byte) 0xBF, bytes[2]);
		String csv = new String(bytes, StandardCharsets.UTF_8);
		assertTrue(csv.contains("\"Item \"\"quoted\"\"\""));
		assertTrue(csv.contains("\"Current KC / attempts\""));
		assertTrue(csv.contains("\"Boss kills: 420\""));
		assertTrue(csv.contains("\"0.8\""));
		assertTrue(csv.startsWith("\ufeff\"Category\",\"Page\",\"Page progress\""));
		assertTrue(csv.lines().findFirst().orElse("").endsWith("\"Item ID\""));
	}

	@Test
	public void appendsChosenExtensionOnlyOnce()
	{
		assertEquals(
			Path.of("report.xlsx"),
			ExporterPanel.withExtension(Path.of("report"), "xlsx"));
		assertEquals(
			Path.of("report.XLSX"),
			ExporterPanel.withExtension(Path.of("report.XLSX"), "xlsx"));
	}

	@Test
	public void toolbarIconIsNativeSizeAndTransparent() throws Exception
	{
		try (java.io.InputStream input = getClass().getResourceAsStream(
			"/com/collectionlogexporter/toolbar_icon.png"))
		{
			assertNotNull(input);
			BufferedImage icon = ImageIO.read(input);
			assertEquals(16, icon.getWidth());
			assertEquals(16, icon.getHeight());
			assertEquals(0, icon.getRGB(0, 0) >>> 24);
		}
	}

	private static ExportData sampleData()
	{
		PageSummary page = new PageSummary(
			"Bosses",
			"Example boss",
			3,
			5,
			2,
			10.5,
			true,
			"Boss kills: 420",
			420,
			2,
			320.0);
		page.setRank(1);
		ItemEstimate estimate = new ItemEstimate("Killing example boss", 20.0, 100.0, 5.0, "Standard");
		ExportRow row = new ExportRow(
			"Bosses",
			"Example boss",
			3,
			5,
			2,
			123,
			"Item \"quoted\"",
			estimate,
			420,
			0.8,
			false,
			0.0,
			true,
			1,
			"Boss kills: 420");
		return new ExportData(
			"Player",
			Instant.parse("2026-07-28T12:00:00Z"),
			"Main",
			910,
			1706,
			910,
			1706,
			Collections.singletonList(page),
			Arrays.asList(row));
	}

	private static String readEntry(ZipFile zip, String name) throws Exception
	{
		try (java.io.InputStream input = zip.getInputStream(zip.getEntry(name)))
		{
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
