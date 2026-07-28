package com.collectionlogexporter;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XlsxExporter
{
	private static final int FILTER_BUTTON_PIXELS = 18;
	private static final int LOGO_DISPLAY_PIXELS = 144;
	private static final long LOGO_EMU = LOGO_DISPLAY_PIXELS * 9_525L;
	private static final DateTimeFormatter EXPORTED_AT =
		DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

	private XlsxExporter()
	{
	}

	static void write(Path target, ExportData data, DetailLevel detailLevel) throws IOException
	{
		List<Sheet> sheets = sheets(data, detailLevel);
		try (OutputStream output = Files.newOutputStream(target);
			 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8))
		{
			int tableCount = (int) sheets.stream().filter(Sheet::hasTable).count();
			put(zip, "[Content_Types].xml", contentTypes(sheets.size(), tableCount));
			put(zip, "_rels/.rels", ROOT_RELS);
			put(zip, "xl/workbook.xml", workbook(sheets));
			put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size()));
			put(zip, "xl/styles.xml", STYLES);
			int tableNumber = 1;
			for (int index = 0; index < sheets.size(); index++)
			{
				Sheet sheet = sheets.get(index);
				int currentTable = sheet.hasTable() ? tableNumber++ : 0;
				writeSheet(zip, index + 1, sheet, currentTable);
				if (currentTable > 0)
				{
					put(zip, "xl/tables/table" + currentTable + ".xml",
						tableXml(currentTable, sheet));
					put(zip, "xl/worksheets/_rels/sheet" + (index + 1) + ".xml.rels",
						sheetTableRelationship(currentTable));
				}
				else if (sheet.hasLogo())
				{
					put(zip, "xl/worksheets/_rels/sheet" + (index + 1) + ".xml.rels",
						sheetDrawingRelationship());
				}
			}
			put(zip, "xl/drawings/drawing1.xml", logoDrawing());
			put(zip, "xl/drawings/_rels/drawing1.xml.rels", logoDrawingRelationship());
			put(zip, "xl/media/image1.png", BrandingAssets.logoBytes());
		}
	}

	private static List<Sheet> sheets(ExportData data, DetailLevel detailLevel)
	{
		List<Sheet> sheets = new ArrayList<>();
		if (detailLevel != DetailLevel.ITEMS)
		{
			List<List<Cell>> rows = new ArrayList<>();
			rows.add(strings("Page priority", "Category", "Page", "Progress", "Obtained",
				"Total", "Remaining", "Estimated page hours", "KC completed",
				"Items at/over target", "Max KC over target", "Current KC / attempts"));
			for (PageSummary page : data.getPages())
			{
				rows.add(Arrays.asList(
					Cell.number(page.getRank()),
					Cell.text(page.getCategory()),
					Cell.text(page.getPage()),
					Cell.text(page.getObtained() + "/" + page.getTotal()),
					Cell.number(page.getObtained()),
					Cell.number(page.getTotal()),
					Cell.number(page.getRemaining()),
					Cell.pageTime(page.isAnytime(), page.getEstimatedHours()),
					Cell.maybeInteger(page.getCompletedAttempts()),
					Cell.number(page.getItemsAtOrOverRate()),
					Cell.maybeNumber(page.getMaxKcOverRate()),
					Cell.text(page.getCurrentAttempts())));
			}
			rows.add(Arrays.asList(
				Cell.text(""),
				Cell.text("TOTALS"),
				Cell.text("Official Collection Log"),
				Cell.text(progress(data.getOfficialObtained(), data.getOfficialTotal())),
				Cell.maybeInteger(data.getOfficialObtained()),
				Cell.maybeInteger(data.getOfficialTotal()),
				Cell.maybeInteger(remaining(data)),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text("Scanned " + progress(data.getScannedObtained(), data.getScannedTotal()))));
			sheets.add(new Sheet("Page summary", "PageSummaryTable", true, rows,
				null));
		}
		if (detailLevel != DetailLevel.SUMMARY)
		{
			List<List<Cell>> rows = new ArrayList<>();
			rows.add(strings(
				"Category", "Page", "Page progress", "Page remaining", "Missing item",
				"Estimated item hours", "Estimated page hours", "Current KC / attempts",
				"Suggested activity", "Page priority", "Attempts per hour",
				"Nominal target attempts", "KC completed", "Attempts remaining to target",
				"KC over target", "Estimate method", "Item ID"));
			for (ExportRow row : data.getRows())
			{
				rows.add(Arrays.asList(
					Cell.text(row.getCategory()),
					Cell.text(row.getPage()),
					Cell.text(row.getObtained() + "/" + row.getTotal()),
					Cell.number(row.getRemaining()),
					Cell.text(row.getItemName()),
					Cell.itemTime(row.isItemAnytime(), row.getEstimatedItemHours()),
					Cell.pageTime(row.isPageAnytime(), row.getEstimatedPageHours()),
					Cell.text(row.getCurrentAttempts()),
					Cell.text(row.getActivity()),
					Cell.number(row.getClosestRank()),
					Cell.maybeNumber(row.getAttemptsPerHour()),
					Cell.maybeNumber(row.getExpectedAdditionalAttempts()),
					Cell.maybeInteger(row.getCompletedAttempts()),
					Cell.maybeNumber(row.getAttemptsToRate()),
					Cell.maybeNumber(row.getKcOverRate()),
					Cell.text(row.getEstimateMethod()),
					Cell.number(row.getItemId())));
			}
			rows.add(Arrays.asList(
				Cell.text("TOTALS"),
				Cell.text("Official Collection Log"),
				Cell.text(progress(data.getOfficialObtained(), data.getOfficialTotal())),
				Cell.maybeInteger(remaining(data)),
				Cell.text("Missing item rows"),
				Cell.text(""),
				Cell.text(""),
				Cell.text("Scanned " + progress(data.getScannedObtained(), data.getScannedTotal())),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text(""),
				Cell.text("")));
			sheets.add(new Sheet("Remaining items", "RemainingItemsTable", true, rows,
				null));
		}

		List<List<Cell>> about = new ArrayList<>();
		about.add(strings("File generated by the GSVS UK ACM Collection Log Exporter. Thank you for using it — please share it with your friends! :)", ""));
		about.add(strings("", ""));
		about.add(strings("", ""));
		about.add(strings("Collection Log Exporter", ""));
		about.add(strings("Player", data.getPlayerName()));
		about.add(strings("Exported", EXPORTED_AT.format(data.getExportedAt())));
		about.add(strings("Estimate profile", data.getEstimateMode()));
		about.add(strings("Scope", "Incomplete Collection Log pages and their missing slots"));
		about.add(strings("Official Collection Log", progress(data.getOfficialObtained(), data.getOfficialTotal())));
		about.add(strings("Scanned unique slots", progress(data.getScannedObtained(), data.getScannedTotal())));
		about.add(strings("Shared slots", "Items displayed on several pages appear once in Remaining items; per-page progress remains native"));
		about.add(strings("Page priority", "1 is the incomplete page estimated to be closest to completion; every missing item on that page shares its priority"));
		about.add(strings("Page ETA", "Credits completed KC toward each drop-rate target; Estimate unavailable if any missing slot has no defensible rate"));
		about.add(strings("Current KC", "Viewed page header first, then public Jagex hiscores, then RuneLite's local killcount cache; blank when unavailable"));
		about.add(strings("Scroll Cases", "Each item uses its matching clue-tier completion count and cumulative milestone; Mimic uses Mimic completions"));
		about.add(strings("Anytime", "Shown when completed KC meets or exceeds the item's nominal target; KC over target shows the excess"));
		about.add(strings("Planning note", "KC credit is a prioritisation heuristic, not a change to independent-drop probability"));
		about.add(strings("Rate scope", "Main estimates may assume tradeable inputs are already acquired and exclude Grand Exchange waiting or cost; Ironman estimates use self-sourcing rates"));
		about.add(strings("Data", "Offline activity speeds and drop rates bundled from Log Adviser; see THIRD_PARTY_NOTICES.md"));
		about.add(strings("Privacy", "Collection Log data stays local; only the display name is sent to official Jagex hiscores for KC lookup"));
		addColumnGuide(about);
		sheets.add(new Sheet("About", null, false, about, new double[]{24, 160}, true));
		return sheets;
	}

	private static void addColumnGuide(List<List<Cell>> about)
	{
		about.add(strings("", ""));
		about.add(strings("Remaining items column", "Meaning"));
		about.add(strings("Category", "Main Collection Log tab: Bosses, Raids, Clues, Minigames or Other"));
		about.add(strings("Page", "Individual Collection Log page containing the missing item"));
		about.add(strings("Page progress", "Slots obtained / total slots displayed on that page"));
		about.add(strings("Page remaining", "Unowned slots still displayed on that page"));
		about.add(strings("Missing item", "Name of the unowned Collection Log item represented by this row"));
		about.add(strings("Estimated item hours", "Planning time remaining after KC credit; values below 10 retain useful decimals, Anytime means the target is reached, and Estimate unavailable means no defensible rate"));
		about.add(strings("Estimated page hours", "Planning time to complete all remaining items on the page; Estimate unavailable means at least one missing item lacks a defensible rate"));
		about.add(strings("Current KC / attempts", "Relevant source counter used for this item, including its source description"));
		about.add(strings("Suggested activity", "Fastest bundled activity for the selected Main or Ironman estimate profile"));
		about.add(strings("Page priority", "Overall page-completion order by estimated page hours; 1 is closest and every item on a page shares this value"));
		about.add(strings("Attempts per hour", "Estimated relevant completions, kills, clues or rolls per hour"));
		about.add(strings("Nominal target attempts", "Nominal drop-rate or milestone target before current KC is credited"));
		about.add(strings("KC completed", "Numeric completed count extracted from Current KC / attempts"));
		about.add(strings("Attempts remaining to target", "Nominal target minus completed KC, with a minimum of zero"));
		about.add(strings("KC over target", "Completed KC above the nominal target, otherwise zero"));
		about.add(strings("Estimate method", "How the bundled estimate is structured, such as Exact, Independent, Sequential or milestone"));
		about.add(strings("Item ID", "RuneScape's internal numeric item identifier, retained for verification"));
		about.add(strings("", ""));
		about.add(strings("Page summary column", "Meaning"));
		about.add(strings("Page priority", "Overall incomplete-page order by estimated completion time; 1 is closest"));
		about.add(strings("Category", "Main Collection Log tab containing the page"));
		about.add(strings("Page", "Individual incomplete Collection Log page"));
		about.add(strings("Progress", "Slots obtained / total slots displayed on the page"));
		about.add(strings("Obtained", "Numeric number of slots obtained on the page"));
		about.add(strings("Total", "Numeric number of slots displayed on the page"));
		about.add(strings("Remaining", "Numeric number of unowned slots displayed on the page"));
		about.add(strings("Estimated page hours", "Planning time to complete all remaining items; Estimate unavailable when coverage is incomplete"));
		about.add(strings("KC completed", "Numeric completed count when the page has one applicable counter"));
		about.add(strings("Items at/over target", "Number of remaining page items whose nominal target has already been reached"));
		about.add(strings("Max KC over target", "Largest amount by which a remaining item is beyond its nominal target"));
		about.add(strings("Current KC / attempts", "Rendered counter text and source used for the page"));
	}

	private static void writeSheet(
		ZipOutputStream zip,
		int sheetNumber,
		Sheet sheet,
		int tableNumber) throws IOException
	{
		zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheetNumber + ".xml"));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"");
		if (tableNumber > 0 || sheet.hasLogo())
		{
			writer.write(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"");
		}
		writer.write(">");
		writer.write("<sheetViews><sheetView workbookViewId=\"0\">");
		if (sheet.rows.size() > 1 && !sheet.hasLogo())
		{
			writer.write("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
		}
		writer.write("</sheetView></sheetViews><cols>");
		for (int index = 0; index < sheet.widths.length; index++)
		{
			int column = index + 1;
			writer.write("<col min=\"" + column + "\" max=\"" + column + "\" width=\""
				+ sheet.widths[index] + "\" customWidth=\"1\"/>");
		}
		writer.write("</cols><sheetData>");
		for (int rowIndex = 0; rowIndex < sheet.rows.size(); rowIndex++)
		{
			List<Cell> row = sheet.rows.get(rowIndex);
			writer.write("<row r=\"" + (rowIndex + 1) + "\"");
			if (sheet.hasLogo() && rowIndex == 1)
			{
				writer.write(" ht=\"108\" customHeight=\"1\"");
			}
			writer.write(">");
			for (int columnIndex = 0; columnIndex < row.size(); columnIndex++)
			{
				if (sheet.hasLogo() && rowIndex == 0 && columnIndex == 1)
				{
					continue;
				}
				writeCell(
					writer,
					rowIndex,
					columnIndex,
					row.get(columnIndex),
					rowIndex == sheet.headerRowIndex(),
					sheet.headerRowIndex(),
					sheet.totalsRow && rowIndex == sheet.rows.size() - 1,
					sheet.hasTable());
			}
			writer.write("</row>");
		}
		writer.write("</sheetData>");
		if (sheet.hasLogo())
		{
			writer.write("<mergeCells count=\"1\"><mergeCell ref=\"A1:B1\"/></mergeCells>");
		}
		if (tableNumber == 0 && !sheet.hasLogo()
			&& sheet.rows.size() > 1 && !sheet.rows.get(0).isEmpty())
		{
			writer.write("<autoFilter ref=\"A1:" + columnName(sheet.rows.get(0).size())
				+ sheet.rows.size() + "\"/>");
		}
		if (tableNumber > 0)
		{
			writer.write("<tableParts count=\"1\"><tablePart r:id=\"rId1\"/></tableParts>");
		}
		else if (sheet.hasLogo())
		{
			writer.write("<drawing r:id=\"rId1\"/>");
		}
		writer.write("</worksheet>");
		writer.flush();
		zip.closeEntry();
	}

	private static void writeCell(
		BufferedWriter writer,
		int row,
		int column,
		Cell cell,
		boolean headerRow,
		int headerRowIndex,
		boolean summaryRow,
		boolean nativeTable) throws IOException
	{
		String reference = columnName(column + 1) + (row + 1);
		int style;
		if (headerRow || summaryRow)
		{
			style = 1;
		}
		else
		{
			boolean shaded = !nativeTable
				&& row > headerRowIndex
				&& (row - headerRowIndex) % 2 == 0;
			style = cell.numeric
				? cell.adaptive
					? shaded ? 8 : 7
					: cell.decimal
						? shaded ? 5 : 2
						: shaded ? 6 : 3
				: shaded ? 4 : 0;
		}
		if (cell.numeric && cell.value.isEmpty())
		{
			writer.write("<c r=\"" + reference + "\" s=\"" + style + "\"/>");
		}
		else if (cell.numeric)
		{
			writer.write("<c r=\"" + reference + "\" s=\"" + style + "\"><v>"
				+ cell.value + "</v></c>");
		}
		else
		{
			writer.write("<c r=\"" + reference + "\" s=\"" + style
				+ "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
				+ XmlSupport.escape(cell.value) + "</t></is></c>");
		}
	}

	private static List<Cell> strings(String... values)
	{
		List<Cell> cells = new ArrayList<>(values.length);
		for (String value : values)
		{
			cells.add(Cell.text(value));
		}
		return cells;
	}

	private static String columnName(int oneBased)
	{
		StringBuilder name = new StringBuilder();
		int value = oneBased;
		while (value > 0)
		{
			value--;
			name.insert(0, (char) ('A' + value % 26));
			value /= 26;
		}
		return name.toString();
	}

	private static String progress(int obtained, int total)
	{
		return obtained >= 0 && total >= 0 ? obtained + "/" + total : "";
	}

	private static int remaining(ExportData data)
	{
		return data.getOfficialObtained() >= 0 && data.getOfficialTotal() >= 0
			? data.getOfficialTotal() - data.getOfficialObtained()
			: -1;
	}

	private static void put(ZipOutputStream zip, String name, String value) throws IOException
	{
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static void put(ZipOutputStream zip, String name, byte[] value) throws IOException
	{
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value);
		zip.closeEntry();
	}

	private static String contentTypes(int sheetCount, int tableCount)
	{
		StringBuilder xml = new StringBuilder(
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
			+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
			+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
			+ "<Default Extension=\"png\" ContentType=\"image/png\"/>"
			+ "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
			+ "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
			+ "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
		for (int index = 1; index <= sheetCount; index++)
		{
			xml.append("<Override PartName=\"/xl/worksheets/sheet").append(index)
				.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
		}
		for (int index = 1; index <= tableCount; index++)
		{
			xml.append("<Override PartName=\"/xl/tables/table").append(index)
				.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml\"/>");
		}
		return xml.append("</Types>").toString();
	}

	private static String tableXml(int tableNumber, Sheet sheet)
	{
		int columnCount = sheet.rows.get(0).size();
		int lastDataRow = sheet.totalsRow ? sheet.rows.size() - 1 : sheet.rows.size();
		String ref = "A1:" + columnName(columnCount) + lastDataRow;
		StringBuilder xml = new StringBuilder(
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<table xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" id=\""
			+ tableNumber + "\" name=\"" + XmlSupport.escape(sheet.tableName)
			+ "\" displayName=\"" + XmlSupport.escape(sheet.tableName)
			+ "\" ref=\"" + ref + "\" headerRowCount=\"1\">");
		xml.append("<autoFilter ref=\"").append(ref).append("\"/>");
		xml.append("<tableColumns count=\"").append(columnCount).append("\">");
		for (int index = 0; index < columnCount; index++)
		{
			xml.append("<tableColumn id=\"").append(index + 1).append("\" name=\"")
				.append(XmlSupport.escape(sheet.rows.get(0).get(index).value)).append("\"");
			xml.append("/>");
		}
		xml.append("</tableColumns><tableStyleInfo name=\"TableStyleMedium4\" "
			+ "showFirstColumn=\"0\" showLastColumn=\"0\" showRowStripes=\"1\" showColumnStripes=\"0\"/>"
			+ "</table>");
		return xml.toString();
	}

	private static String sheetTableRelationship(int tableNumber)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/table\" "
			+ "Target=\"../tables/table" + tableNumber + ".xml\"/>"
			+ "</Relationships>";
	}

	private static String sheetDrawingRelationship()
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" "
			+ "Target=\"../drawings/drawing1.xml\"/>"
			+ "</Relationships>";
	}

	private static String logoDrawingRelationship()
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
			+ "Target=\"../media/image1.png\"/>"
			+ "</Relationships>";
	}

	private static String logoDrawing()
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" "
			+ "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
			+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
			+ "<xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff>"
			+ "<xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>"
			+ "<xdr:ext cx=\"" + LOGO_EMU + "\" cy=\"" + LOGO_EMU + "\"/>"
			+ "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"2\" name=\"Collection Log Exporter logo\"/>"
			+ "<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>"
			+ "<xdr:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>"
			+ "<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + LOGO_EMU
			+ "\" cy=\"" + LOGO_EMU + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/>"
			+ "</a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>";
	}

	private static String workbook(List<Sheet> sheets)
	{
		StringBuilder xml = new StringBuilder(
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
			+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
		for (int index = 0; index < sheets.size(); index++)
		{
			xml.append("<sheet name=\"").append(XmlSupport.escape(sheets.get(index).name))
				.append("\" sheetId=\"").append(index + 1).append("\" r:id=\"rId")
				.append(index + 1).append("\"/>");
		}
		return xml.append("</sheets></workbook>").toString();
	}

	private static String workbookRelationships(int sheetCount)
	{
		StringBuilder xml = new StringBuilder(
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
		for (int index = 1; index <= sheetCount; index++)
		{
			xml.append("<Relationship Id=\"rId").append(index)
				.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
				.append(index).append(".xml\"/>");
		}
		xml.append("<Relationship Id=\"rId").append(sheetCount + 1)
			.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
		return xml.append("</Relationships>").toString();
	}

	private static final String ROOT_RELS =
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
		+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
		+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
		+ "</Relationships>";

	private static final String STYLES =
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
		+ "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
		+ "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"[&lt;10]0.###;0\"/></numFmts>"
		+ "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
		+ "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
		+ "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill>"
		+ "<fill><patternFill patternType=\"gray125\"/></fill>"
		+ "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF355E3B\"/><bgColor indexed=\"64\"/></patternFill></fill>"
		+ "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEAF2EC\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
		+ "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
		+ "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
		+ "<cellXfs count=\"9\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
		+ "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
		+ "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
		+ "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
		+ "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>"
		+ "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\"/>"
		+ "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\"/>"
		+ "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
		+ "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\"/></cellXfs>"
		+ "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
		+ "</styleSheet>";

	private static final class Sheet
	{
		private final String name;
		private final String tableName;
		private final boolean totalsRow;
		private final boolean logo;
		private final List<List<Cell>> rows;
		private final double[] widths;

		private Sheet(
			String name,
			String tableName,
			boolean totalsRow,
			List<List<Cell>> rows,
			double[] widths)
		{
			this(name, tableName, totalsRow, rows, widths, false);
		}

		private Sheet(
			String name,
			String tableName,
			boolean totalsRow,
			List<List<Cell>> rows,
			double[] widths,
			boolean logo)
		{
			this.name = name;
			this.tableName = tableName;
			this.totalsRow = totalsRow;
			this.logo = logo;
			this.rows = rows;
			this.widths = autoWidths(rows, widths, tableName != null, headerRowIndex());
		}

		private boolean hasTable()
		{
			return tableName != null;
		}

		private boolean hasLogo()
		{
			return logo;
		}

		private int headerRowIndex()
		{
			return logo ? 3 : 0;
		}

		private static double[] autoWidths(
			List<List<Cell>> rows,
			double[] caps,
			boolean nativeTable,
			int headerRowIndex)
		{
			int columnCount = caps == null
				? rows.get(0).size()
				: caps.length;
			double[] widths = new double[columnCount];
			BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try
			{
				FontMetrics metrics = graphics.getFontMetrics(new Font("Calibri", Font.PLAIN, 11));
				FontMetrics headerMetrics = graphics.getFontMetrics(new Font("Calibri", Font.BOLD, 11));
				double widthUnit = Math.max(1.0, metrics.charWidth('0'));
				for (int column = 0; column < columnCount; column++)
				{
					int widestPixels = 0;
					for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
					{
						List<Cell> row = rows.get(rowIndex);
						if (column < row.size())
						{
							int cellPixels = (rowIndex == headerRowIndex ? headerMetrics : metrics)
								.stringWidth(row.get(column).displayValue());
							if (nativeTable && rowIndex == 0)
							{
								cellPixels += FILTER_BUTTON_PIXELS;
							}
							widestPixels = Math.max(
								widestPixels,
								cellPixels);
						}
					}
					double maximum = caps == null ? 255.0 : caps[column];
					widths[column] = Math.min(
						maximum,
						Math.max(8.0, widestPixels / widthUnit + 2.5));
				}
			}
			finally
			{
				graphics.dispose();
			}
			return widths;
		}
	}

	private static final class Cell
	{
		private final String value;
		private final boolean numeric;
		private final boolean decimal;
		private final boolean adaptive;

		private Cell(String value, boolean numeric, boolean decimal, boolean adaptive)
		{
			this.value = value;
			this.numeric = numeric;
			this.decimal = decimal;
			this.adaptive = adaptive;
		}

		private static Cell text(String value)
		{
			return new Cell(value == null ? "" : value, false, false, false);
		}

		private static Cell number(double value)
		{
			return new Cell(Double.toString(value), true, false, false);
		}

		private static Cell maybeNumber(double value)
		{
			return Double.isFinite(value) ? number(value) : new Cell("", true, false, false);
		}

		private static Cell maybeInteger(int value)
		{
			return value >= 0 ? number(value) : new Cell("", true, false, false);
		}

		private static Cell itemTime(boolean anytime, double hours)
		{
			return anytime
				? text("Anytime")
				: Double.isFinite(hours)
					? new Cell(Double.toString(hours), true, false, true)
					: text("Estimate unavailable");
		}

		private static Cell pageTime(boolean anytime, double hours)
		{
			return anytime
				? text("Anytime")
				: Double.isFinite(hours)
					? new Cell(Double.toString(hours), true, true, false)
					: text("Estimate unavailable");
		}

		private String displayValue()
		{
			if (!numeric || value.isEmpty())
			{
				return value;
			}
			try
			{
				double number = Double.parseDouble(value);
				if (adaptive)
				{
					if (Math.abs(number) < 10.0)
					{
						return String.format(java.util.Locale.ENGLISH, "%.3f", number)
							.replaceAll("0+$", "")
							.replaceAll("\\.$", "");
					}
					return Long.toString(Math.round(number));
				}
				return decimal
					? String.format(java.util.Locale.ENGLISH, "%.2f", number)
					: Long.toString(Math.round(number));
			}
			catch (NumberFormatException ignored)
			{
				return value;
			}
		}
	}
}
