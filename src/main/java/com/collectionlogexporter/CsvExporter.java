package com.collectionlogexporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class CsvExporter
{
	private CsvExporter()
	{
	}

	static void write(Path target, ExportData data, DetailLevel detailLevel) throws IOException
	{
		try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8))
		{
			// UTF-8 BOM keeps non-ASCII item/page names correct in older Excel versions.
			writer.write('\ufeff');
			if (detailLevel == DetailLevel.SUMMARY)
			{
				writeSummary(writer, data);
			}
			else
			{
				// CSV has one table, so BOTH uses the detailed table with page fields repeated.
				writeItems(writer, data);
			}
		}
	}

	private static void writeSummary(BufferedWriter writer, ExportData data) throws IOException
	{
		writeRow(writer, Arrays.asList(
			"Page priority",
			"Category",
			"Page",
			"Progress",
			"Obtained",
			"Total",
			"Remaining",
			"Estimated page hours",
			"KC completed",
			"Items at/over target",
			"Max KC over target",
			"Current KC / attempts"));
		for (PageSummary page : data.getPages())
		{
			writeRow(writer, Arrays.asList(
				Integer.toString(page.getRank()),
				page.getCategory(),
				page.getPage(),
				page.getObtained() + "/" + page.getTotal(),
				Integer.toString(page.getObtained()),
				Integer.toString(page.getTotal()),
				Integer.toString(page.getRemaining()),
				pageTime(page.isAnytime(), page.getEstimatedHours()),
				integer(page.getCompletedAttempts()),
				Integer.toString(page.getItemsAtOrOverRate()),
				wholeNumber(page.getMaxKcOverRate()),
				page.getCurrentAttempts()));
		}
		writeRow(writer, Arrays.asList(
			"",
			"TOTALS",
			"Official Collection Log",
			progress(data.getOfficialObtained(), data.getOfficialTotal()),
			integer(data.getOfficialObtained()),
			integer(data.getOfficialTotal()),
			data.getOfficialObtained() >= 0 && data.getOfficialTotal() >= 0
				? Integer.toString(data.getOfficialTotal() - data.getOfficialObtained())
				: "",
			"",
			"",
			"",
			"",
			"Scanned " + progress(data.getScannedObtained(), data.getScannedTotal())));
	}

	private static void writeItems(BufferedWriter writer, ExportData data) throws IOException
	{
		writeRow(writer, Arrays.asList(
			"Category",
			"Page",
			"Page progress",
			"Page remaining",
			"Missing item",
			"Estimated item hours",
			"Estimated page hours",
			"Current KC / attempts",
			"Suggested activity",
			"Page priority",
			"Attempts per hour",
			"Nominal target attempts",
			"KC completed",
			"Attempts remaining to target",
			"KC over target",
			"Estimate method",
			"Item ID"));
		for (ExportRow row : data.getRows())
		{
			writeRow(writer, Arrays.asList(
				row.getCategory(),
				row.getPage(),
				row.getObtained() + "/" + row.getTotal(),
				Integer.toString(row.getRemaining()),
				row.getItemName(),
				itemTime(row.isItemAnytime(), row.getEstimatedItemHours()),
				pageTime(row.isPageAnytime(), row.getEstimatedPageHours()),
				row.getCurrentAttempts(),
				row.getActivity(),
				Integer.toString(row.getClosestRank()),
				wholeNumber(row.getAttemptsPerHour()),
				wholeNumber(row.getExpectedAdditionalAttempts()),
				integer(row.getCompletedAttempts()),
				wholeNumber(row.getAttemptsToRate()),
				wholeNumber(row.getKcOverRate()),
				row.getEstimateMethod(),
				Integer.toString(row.getItemId())));
		}
		writeRow(writer, Arrays.asList(
			"TOTALS",
			"Official Collection Log",
			progress(data.getOfficialObtained(), data.getOfficialTotal()),
			data.getOfficialObtained() >= 0 && data.getOfficialTotal() >= 0
				? Integer.toString(data.getOfficialTotal() - data.getOfficialObtained())
				: "",
			"Missing item rows",
			"",
			"",
			"Scanned " + progress(data.getScannedObtained(), data.getScannedTotal()),
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			""));
	}

	private static String wholeNumber(double value)
	{
		return Double.isFinite(value) ? Long.toString(Math.round(value)) : "";
	}

	private static String integer(int value)
	{
		return value >= 0 ? Integer.toString(value) : "";
	}

	private static String itemTime(boolean anytime, double hours)
	{
		return anytime || !Double.isFinite(hours)
			? anytime ? "Anytime" : "Estimate unavailable"
			: Math.abs(hours) < 10.0
				? trimDecimal(hours)
				: Long.toString(Math.round(hours));
	}

	private static String pageTime(boolean anytime, double hours)
	{
		return anytime || !Double.isFinite(hours)
			? anytime ? "Anytime" : "Estimate unavailable"
			: String.format(java.util.Locale.ENGLISH, "%.2f", hours);
	}

	private static String trimDecimal(double value)
	{
		return String.format(java.util.Locale.ENGLISH, "%.3f", value)
			.replaceAll("0+$", "")
			.replaceAll("\\.$", "");
	}

	private static String progress(int obtained, int total)
	{
		return obtained >= 0 && total >= 0 ? obtained + "/" + total : "";
	}

	private static void writeRow(BufferedWriter writer, List<String> values) throws IOException
	{
		for (int index = 0; index < values.size(); index++)
		{
			if (index > 0)
			{
				writer.write(',');
			}
			writer.write(quote(values.get(index)));
		}
		writer.newLine();
	}

	static String quote(String value)
	{
		String safe = value == null ? "" : value;
		return "\"" + safe.replace("\"", "\"\"") + "\"";
	}
}
