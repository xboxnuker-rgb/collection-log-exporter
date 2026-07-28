package com.collectionlogexporter;

public enum EstimateMode
{
	AUTO("Auto-detect"),
	MAIN("Main"),
	IRONMAN("Ironman");

	private final String label;

	EstimateMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
