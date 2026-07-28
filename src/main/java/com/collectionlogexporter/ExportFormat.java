package com.collectionlogexporter;

enum ExportFormat
{
	XLSX("Excel workbook (.xlsx)", "xlsx"),
	ODS("OpenDocument spreadsheet (.ods)", "ods"),
	CSV("Universal CSV (.csv)", "csv");

	private final String label;
	private final String extension;

	ExportFormat(String label, String extension)
	{
		this.label = label;
		this.extension = extension;
	}

	String getExtension()
	{
		return extension;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
