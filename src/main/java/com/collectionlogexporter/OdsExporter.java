package com.collectionlogexporter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class OdsExporter
{
	private static final String MIME = "application/vnd.oasis.opendocument.spreadsheet";
	private static final String SUPPORT_LABEL = "Support GSVS UK ACM on Patreon";
	private static final String SUPPORT_URL =
		"https://www.patreon.com/GSVS_UK_ACM/posts/buy-us-virtual-165207029";
	private static final DateTimeFormatter EXPORTED_AT =
		DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

	private OdsExporter()
	{
	}

	static void write(Path target, ExportData data, DetailLevel detailLevel) throws IOException
	{
		try (OutputStream output = Files.newOutputStream(target);
			 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8))
		{
			putStored(zip, "mimetype", MIME);
			put(zip, "META-INF/manifest.xml", MANIFEST);
			put(zip, "styles.xml", STYLES);
			put(zip, "content.xml", content(data, detailLevel));
			put(zip, "Pictures/logo.png", BrandingAssets.logoBytes());
		}
	}

	private static String content(ExportData data, DetailLevel detailLevel)
	{
		StringBuilder xml = new StringBuilder(256_000);
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
			.append("<office:document-content ")
			.append("xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ")
			.append("xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" ")
			.append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" ")
			.append("xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" ")
			.append("xmlns:number=\"urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0\" ")
			.append("xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" ")
			.append("xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" ")
			.append("xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\" ")
			.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
			.append("office:version=\"1.2\"><office:automatic-styles>")
			.append("<number:number-style style:name=\"whole\"><number:number number:decimal-places=\"0\"/></number:number-style>")
			.append("<number:number-style style:name=\"decimal\"><number:number number:decimal-places=\"2\"/></number:number-style>")
			.append("<number:number-style style:name=\"adaptive\"><number:number number:decimal-places=\"3\" number:min-decimal-places=\"0\"/></number:number-style>")
			.append("<style:style style:name=\"header\" style:family=\"table-cell\">")
			.append("<style:text-properties fo:font-weight=\"bold\"/>")
			.append("</style:style>")
			.append("<style:style style:name=\"wholeCell\" style:family=\"table-cell\" style:data-style-name=\"whole\"/>")
			.append("<style:style style:name=\"decimalCell\" style:family=\"table-cell\" style:data-style-name=\"decimal\"/>")
			.append("<style:style style:name=\"adaptiveCell\" style:family=\"table-cell\" style:data-style-name=\"adaptive\"/>")
			.append("<style:style style:name=\"shade\" style:family=\"table-cell\"><style:table-cell-properties fo:background-color=\"#eaf2ec\"/></style:style>")
			.append("<style:style style:name=\"shadeWhole\" style:family=\"table-cell\" style:data-style-name=\"whole\"><style:table-cell-properties fo:background-color=\"#eaf2ec\"/></style:style>")
			.append("<style:style style:name=\"shadeDecimal\" style:family=\"table-cell\" style:data-style-name=\"decimal\"><style:table-cell-properties fo:background-color=\"#eaf2ec\"/></style:style>")
			.append("<style:style style:name=\"shadeAdaptive\" style:family=\"table-cell\" style:data-style-name=\"adaptive\"><style:table-cell-properties fo:background-color=\"#eaf2ec\"/></style:style>")
			.append("<style:style style:name=\"logoRow\" style:family=\"table-row\"><style:table-row-properties style:row-height=\"1.5in\"/></style:style>")
			.append("</office:automatic-styles><office:body><office:spreadsheet>");

		if (detailLevel != DetailLevel.ITEMS)
		{
			List<List<Value>> rows = new ArrayList<>();
			rows.add(texts("Page priority", "Category", "Page", "Progress", "Obtained",
				"Total", "Remaining", "Estimated page hours", "KC completed",
				"Items at/over target", "Max KC over target", "Current KC / attempts"));
			for (PageSummary page : data.getPages())
			{
				rows.add(Arrays.asList(
					Value.number(page.getRank()),
					Value.text(page.getCategory()),
					Value.text(page.getPage()),
					Value.text(page.getObtained() + "/" + page.getTotal()),
					Value.number(page.getObtained()),
					Value.number(page.getTotal()),
					Value.number(page.getRemaining()),
					Value.pageTime(page.isAnytime(), page.getEstimatedHours()),
					Value.maybeInteger(page.getCompletedAttempts()),
					Value.number(page.getItemsAtOrOverRate()),
					Value.maybeNumber(page.getMaxKcOverRate()),
					Value.text(page.getCurrentAttempts())));
			}
			rows.add(Arrays.asList(
				Value.text(""),
				Value.text("TOTALS"),
				Value.text("Official Collection Log"),
				Value.text(progress(data.getOfficialObtained(), data.getOfficialTotal())),
				Value.maybeInteger(data.getOfficialObtained()),
				Value.maybeInteger(data.getOfficialTotal()),
				Value.maybeInteger(remaining(data)),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text("Scanned " + progress(data.getScannedObtained(), data.getScannedTotal()))));
			appendTable(xml, "Page summary", rows);
		}

		if (detailLevel != DetailLevel.SUMMARY)
		{
			List<List<Value>> rows = new ArrayList<>();
			rows.add(texts(
				"Category", "Page", "Page progress", "Page remaining", "Missing item",
				"Estimated item hours", "Estimated page hours", "Current KC / attempts",
				"Suggested activity", "Page priority", "Attempts per hour",
				"Nominal target attempts", "KC completed", "Attempts remaining to target",
				"KC over target", "Estimate method", "Item ID"));
			for (ExportRow row : data.getRows())
			{
				rows.add(Arrays.asList(
					Value.text(row.getCategory()),
					Value.text(row.getPage()),
					Value.text(row.getObtained() + "/" + row.getTotal()),
					Value.number(row.getRemaining()),
					Value.text(row.getItemName()),
					Value.itemTime(row.isItemAnytime(), row.getEstimatedItemHours()),
					Value.pageTime(row.isPageAnytime(), row.getEstimatedPageHours()),
					Value.text(row.getCurrentAttempts()),
					Value.text(row.getActivity()),
					Value.number(row.getClosestRank()),
					Value.maybeNumber(row.getAttemptsPerHour()),
					Value.maybeNumber(row.getExpectedAdditionalAttempts()),
					Value.maybeInteger(row.getCompletedAttempts()),
					Value.maybeNumber(row.getAttemptsToRate()),
					Value.maybeNumber(row.getKcOverRate()),
					Value.text(row.getEstimateMethod()),
					Value.number(row.getItemId())));
			}
			rows.add(Arrays.asList(
				Value.text("TOTALS"),
				Value.text("Official Collection Log"),
				Value.text(progress(data.getOfficialObtained(), data.getOfficialTotal())),
				Value.maybeInteger(remaining(data)),
				Value.text("Missing item rows"),
				Value.text(""),
				Value.text(""),
				Value.text("Scanned " + progress(data.getScannedObtained(), data.getScannedTotal())),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text(""),
				Value.text("")));
			appendTable(xml, "Remaining items", rows);
		}

		List<List<Value>> about = new ArrayList<>();
		about.add(texts("File generated by the GSVS UK ACM Collection Log Exporter. Thank you for using it — please share it with your friends! :)", ""));
		about.add(Arrays.asList(Value.logo(), Value.text("")));
		about.add(Arrays.asList(Value.hyperlink(SUPPORT_LABEL, SUPPORT_URL), Value.text("")));
		about.add(texts("Collection Log Exporter", ""));
		about.add(texts("Player", data.getPlayerName()));
		about.add(texts("Exported", EXPORTED_AT.format(data.getExportedAt())));
		about.add(texts("Estimate profile", data.getEstimateMode()));
		about.add(texts("Scope", "Incomplete Collection Log pages and their missing slots"));
		about.add(texts("Official Collection Log", progress(data.getOfficialObtained(), data.getOfficialTotal())));
		about.add(texts("Scanned unique slots", progress(data.getScannedObtained(), data.getScannedTotal())));
		about.add(texts("Shared slots", "Items displayed on several pages appear once in Remaining items; per-page progress remains native"));
		about.add(texts("Page priority", "1 is the incomplete page estimated to be closest to completion; every missing item on that page shares its priority"));
		about.add(texts("Page ETA", "Credits completed KC toward each drop-rate target; Estimate unavailable when coverage is incomplete"));
		about.add(texts("Current KC", "Viewed page header first, then public Jagex hiscores, then RuneLite's local killcount cache; blank when unavailable"));
		about.add(texts("Scroll Cases", "Each item uses its matching clue-tier completion count and cumulative milestone; Mimic uses Mimic completions"));
		about.add(texts("Anytime", "Shown when completed KC meets or exceeds the item's nominal target; KC over target shows the excess"));
		about.add(texts("Planning note", "KC credit is a prioritisation heuristic, not a change to independent-drop probability"));
		about.add(texts("Rate scope", "Main estimates may assume tradeable inputs are already acquired and exclude Grand Exchange waiting or cost; Ironman estimates use self-sourcing rates"));
		about.add(texts("Privacy", "Collection Log data stays local; only the display name is sent to official Jagex hiscores for KC lookup"));
		addColumnGuide(about);
		appendTable(xml, "About", about);

		return xml.append("</office:spreadsheet></office:body></office:document-content>").toString();
	}

	private static void appendTable(StringBuilder xml, String name, List<List<Value>> rows)
	{
		boolean about = "About".equals(name);
		int headerRowIndex = about ? 3 : 0;
		xml.append("<table:table table:name=\"").append(XmlSupport.escape(name)).append("\">");
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
		{
			xml.append("<table:table-row");
			if (about && rowIndex == 1)
			{
				xml.append(" table:style-name=\"logoRow\"");
			}
			xml.append(">");
			if (about && rowIndex == 0)
			{
				Value intro = rows.get(rowIndex).get(0);
				xml.append("<table:table-cell table:number-columns-spanned=\"2\" ")
					.append("office:value-type=\"string\"><text:p>")
					.append(XmlSupport.escape(intro.value))
					.append("</text:p></table:table-cell><table:covered-table-cell/>")
					.append("</table:table-row>");
				continue;
			}
			for (Value value : rows.get(rowIndex))
			{
				String style = style(rowIndex, headerRowIndex, value);
				if (value.logo)
				{
					xml.append("<table:table-cell office:value-type=\"string\"><text:p>")
						.append("<draw:frame draw:name=\"Collection Log Exporter logo\" text:anchor-type=\"as-char\" ")
						.append("svg:width=\"1.5in\" svg:height=\"1.5in\"><draw:image ")
						.append("xlink:href=\"Pictures/logo.png\" xlink:type=\"simple\" ")
						.append("xlink:show=\"embed\" xlink:actuate=\"onLoad\"/>")
						.append("</draw:frame></text:p></table:table-cell>");
				}
				else if (value.hyperlink != null)
				{
					xml.append("<table:table-cell").append(style)
						.append(" office:value-type=\"string\"><text:p><text:a xlink:href=\"")
						.append(XmlSupport.escape(value.hyperlink))
						.append("\" xlink:type=\"simple\">")
						.append(XmlSupport.escape(value.value))
						.append("</text:a></text:p></table:table-cell>");
				}
				else if (value.numeric && !value.value.isEmpty())
				{
					xml.append("<table:table-cell").append(style)
						.append(" office:value-type=\"float\" office:value=\"")
						.append(value.value).append("\"><text:p>")
						.append(value.value).append("</text:p></table:table-cell>");
				}
				else
				{
					xml.append("<table:table-cell").append(style)
						.append(" office:value-type=\"string\"><text:p>")
						.append(XmlSupport.escape(value.value))
						.append("</text:p></table:table-cell>");
				}
			}
			xml.append("</table:table-row>");
		}
		xml.append("</table:table>");
	}

	private static String style(int rowIndex, int headerRowIndex, Value value)
	{
		if (rowIndex == headerRowIndex)
		{
			return " table:style-name=\"header\"";
		}
		boolean shaded = rowIndex > headerRowIndex
			&& (rowIndex - headerRowIndex) % 2 == 0;
		String name;
		if (!value.numeric)
		{
			name = shaded ? "shade" : "";
		}
		else if (value.adaptive)
		{
			name = shaded ? "shadeAdaptive" : "adaptiveCell";
		}
		else if (value.decimal)
		{
			name = shaded ? "shadeDecimal" : "decimalCell";
		}
		else
		{
			name = shaded ? "shadeWhole" : "wholeCell";
		}
		return name.isEmpty() ? "" : " table:style-name=\"" + name + "\"";
	}

	private static void addColumnGuide(List<List<Value>> about)
	{
		about.add(texts("", ""));
		about.add(texts("Remaining items column", "Meaning"));
		about.add(texts("Category", "Main Collection Log tab: Bosses, Raids, Clues, Minigames or Other"));
		about.add(texts("Page", "Individual Collection Log page containing the missing item"));
		about.add(texts("Page progress", "Slots obtained / total slots displayed on that page"));
		about.add(texts("Page remaining", "Unowned slots still displayed on that page"));
		about.add(texts("Missing item", "Name of the unowned Collection Log item represented by this row"));
		about.add(texts("Estimated item hours", "Planning time remaining after KC credit; values below 10 retain useful decimals, Anytime means the target is reached, and Estimate unavailable means no defensible rate"));
		about.add(texts("Estimated page hours", "Planning time to complete all remaining items on the page; Estimate unavailable means at least one missing item lacks a defensible rate"));
		about.add(texts("Current KC / attempts", "Relevant source counter used for this item, including its source description"));
		about.add(texts("Suggested activity", "Fastest bundled activity for the selected Main or Ironman estimate profile"));
		about.add(texts("Page priority", "Overall page-completion order by estimated page hours; 1 is closest and every item on a page shares this value"));
		about.add(texts("Attempts per hour", "Estimated relevant completions, kills, clues or rolls per hour"));
		about.add(texts("Nominal target attempts", "Nominal drop-rate or milestone target before current KC is credited"));
		about.add(texts("KC completed", "Numeric completed count extracted from Current KC / attempts"));
		about.add(texts("Attempts remaining to target", "Nominal target minus completed KC, with a minimum of zero"));
		about.add(texts("KC over target", "Completed KC above the nominal target, otherwise zero"));
		about.add(texts("Estimate method", "How the bundled estimate is structured, such as Exact, Independent, Sequential or milestone"));
		about.add(texts("Item ID", "RuneScape's internal numeric item identifier, retained for verification"));
		about.add(texts("", ""));
		about.add(texts("Page summary column", "Meaning"));
		about.add(texts("Page priority", "Overall incomplete-page order by estimated completion time; 1 is closest"));
		about.add(texts("Category", "Main Collection Log tab containing the page"));
		about.add(texts("Page", "Individual incomplete Collection Log page"));
		about.add(texts("Progress", "Slots obtained / total slots displayed on the page"));
		about.add(texts("Obtained", "Numeric number of slots obtained on the page"));
		about.add(texts("Total", "Numeric number of slots displayed on the page"));
		about.add(texts("Remaining", "Numeric number of unowned slots displayed on the page"));
		about.add(texts("Estimated page hours", "Planning time to complete all remaining items; Estimate unavailable when coverage is incomplete"));
		about.add(texts("KC completed", "Numeric completed count when the page has one applicable counter"));
		about.add(texts("Items at/over target", "Number of remaining page items whose nominal target has already been reached"));
		about.add(texts("Max KC over target", "Largest amount by which a remaining item is beyond its nominal target"));
		about.add(texts("Current KC / attempts", "Rendered counter text and source used for the page"));
	}

	private static List<Value> texts(String... values)
	{
		List<Value> row = new ArrayList<>(values.length);
		for (String value : values)
		{
			row.add(Value.text(value));
		}
		return row;
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

	private static void putStored(ZipOutputStream zip, String name, String value) throws IOException
	{
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		CRC32 crc = new CRC32();
		crc.update(bytes);
		ZipEntry entry = new ZipEntry(name);
		entry.setMethod(ZipEntry.STORED);
		entry.setSize(bytes.length);
		entry.setCompressedSize(bytes.length);
		entry.setCrc(crc.getValue());
		zip.putNextEntry(entry);
		zip.write(bytes);
		zip.closeEntry();
	}

	private static final String MANIFEST =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
		+ "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">"
		+ "<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"" + MIME + "\"/>"
		+ "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
		+ "<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>"
		+ "<manifest:file-entry manifest:full-path=\"Pictures/\" manifest:media-type=\"\"/>"
		+ "<manifest:file-entry manifest:full-path=\"Pictures/logo.png\" manifest:media-type=\"image/png\"/>"
		+ "</manifest:manifest>";

	private static final String STYLES =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
		+ "<office:document-styles xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
		+ "xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" office:version=\"1.2\">"
		+ "<office:styles/></office:document-styles>";

	private static final class Value
	{
		private final String value;
		private final boolean numeric;
		private final boolean decimal;
		private final boolean adaptive;
		private final boolean logo;
		private final String hyperlink;

		private Value(
			String value,
			boolean numeric,
			boolean decimal,
			boolean adaptive,
			boolean logo)
		{
			this(value, numeric, decimal, adaptive, logo, null);
		}

		private Value(
			String value,
			boolean numeric,
			boolean decimal,
			boolean adaptive,
			boolean logo,
			String hyperlink)
		{
			this.value = value;
			this.numeric = numeric;
			this.decimal = decimal;
			this.adaptive = adaptive;
			this.logo = logo;
			this.hyperlink = hyperlink;
		}

		private static Value text(String value)
		{
			return new Value(value == null ? "" : value, false, false, false, false);
		}

		private static Value logo()
		{
			return new Value("", false, false, false, true);
		}

		private static Value hyperlink(String value, String url)
		{
			return new Value(
				value == null ? "" : value,
				false,
				false,
				false,
				false,
				url);
		}

		private static Value number(double value)
		{
			return new Value(Double.toString(value), true, false, false, false);
		}

		private static Value maybeNumber(double value)
		{
			return Double.isFinite(value) ? number(value) : new Value("", true, false, false, false);
		}

		private static Value maybeInteger(int value)
		{
			return value >= 0 ? number(value) : new Value("", true, false, false, false);
		}

		private static Value itemTime(boolean anytime, double hours)
		{
			return anytime
				? text("Anytime")
				: Double.isFinite(hours)
					? new Value(Double.toString(hours), true, false, true, false)
					: text("Estimate unavailable");
		}

		private static Value pageTime(boolean anytime, double hours)
		{
			return anytime
				? text("Anytime")
				: Double.isFinite(hours)
					? new Value(Double.toString(hours), true, true, false, false)
					: text("Estimate unavailable");
		}
	}
}
