package com.collectionlogexporter;

enum SortMode
{
	CLOSEST("Closest estimated completion"),
	FEWEST("Fewest slots remaining"),
	LOG_ORDER("Collection Log order");

	private final String label;

	SortMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
