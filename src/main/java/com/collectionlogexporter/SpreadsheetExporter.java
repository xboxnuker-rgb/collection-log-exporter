package com.collectionlogexporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class SpreadsheetExporter
{
	private SpreadsheetExporter()
	{
	}

	static void write(Path target, ExportData data, ExportOptions options) throws IOException
	{
		Path absolute = target.toAbsolutePath();
		Path parent = absolute.getParent();
		if (parent == null)
		{
			throw new IOException("Export destination has no parent directory");
		}
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, ".collection-log-export-", ".tmp");
		boolean moved = false;
		try
		{
			switch (options.getFormat())
			{
				case XLSX:
					XlsxExporter.write(temporary, data, options.getDetailLevel());
					break;
				case ODS:
					OdsExporter.write(temporary, data, options.getDetailLevel());
					break;
				case CSV:
					CsvExporter.write(temporary, data, options.getDetailLevel());
					break;
				default:
					throw new IOException("Unsupported export format: " + options.getFormat());
			}
			try
			{
				Files.move(
					temporary,
					absolute,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException atomicFailure)
			{
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		}
		finally
		{
			if (!moved)
			{
				Files.deleteIfExists(temporary);
			}
		}
	}
}
