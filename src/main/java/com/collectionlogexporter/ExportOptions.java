package com.collectionlogexporter;

final class ExportOptions
{
	private final ExportFormat format;
	private final DetailLevel detailLevel;
	private final SortMode sortMode;
	private final EstimateMode estimateMode;

	ExportOptions(
		ExportFormat format,
		DetailLevel detailLevel,
		SortMode sortMode,
		EstimateMode estimateMode)
	{
		this.format = format;
		this.detailLevel = detailLevel;
		this.sortMode = sortMode;
		this.estimateMode = estimateMode;
	}

	ExportFormat getFormat() { return format; }
	DetailLevel getDetailLevel() { return detailLevel; }
	SortMode getSortMode() { return sortMode; }
	EstimateMode getEstimateMode() { return estimateMode; }
}
