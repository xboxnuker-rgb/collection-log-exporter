package com.collectionlogexporter;

enum DetailLevel
{
	BOTH("Page summary + missing items"),
	SUMMARY("Page summary only"),
	ITEMS("Missing items only");

	private final String label;

	DetailLevel(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
