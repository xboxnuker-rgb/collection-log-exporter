package com.collectionlogexporter;

import java.io.IOException;
import java.io.InputStream;

final class BrandingAssets
{
	private static final String LOGO_RESOURCE = "/com/collectionlogexporter/logo.png";

	private BrandingAssets()
	{
	}

	static byte[] logoBytes() throws IOException
	{
		try (InputStream input = BrandingAssets.class.getResourceAsStream(LOGO_RESOURCE))
		{
			if (input == null)
			{
				throw new IOException("Missing bundled logo resource " + LOGO_RESOURCE);
			}
			return input.readAllBytes();
		}
	}
}
