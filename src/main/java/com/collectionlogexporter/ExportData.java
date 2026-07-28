package com.collectionlogexporter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

final class ExportData
{
	private final String playerName;
	private final Instant exportedAt;
	private final String estimateMode;
	private final int officialObtained;
	private final int officialTotal;
	private final int scannedObtained;
	private final int scannedTotal;
	private final List<PageSummary> pages;
	private final List<ExportRow> rows;

	ExportData(
		String playerName,
		Instant exportedAt,
		String estimateMode,
		int officialObtained,
		int officialTotal,
		int scannedObtained,
		int scannedTotal,
		List<PageSummary> pages,
		List<ExportRow> rows)
	{
		this.playerName = playerName;
		this.exportedAt = exportedAt;
		this.estimateMode = estimateMode;
		this.officialObtained = officialObtained;
		this.officialTotal = officialTotal;
		this.scannedObtained = scannedObtained;
		this.scannedTotal = scannedTotal;
		this.pages = Collections.unmodifiableList(pages);
		this.rows = Collections.unmodifiableList(rows);
	}

	String getPlayerName() { return playerName; }
	Instant getExportedAt() { return exportedAt; }
	String getEstimateMode() { return estimateMode; }
	int getOfficialObtained() { return officialObtained; }
	int getOfficialTotal() { return officialTotal; }
	int getScannedObtained() { return scannedObtained; }
	int getScannedTotal() { return scannedTotal; }
	List<PageSummary> getPages() { return pages; }
	List<ExportRow> getRows() { return rows; }
}
