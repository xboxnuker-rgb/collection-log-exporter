package com.collectionlogexporter;

import java.util.Arrays;

final class PageDefinition
{
	private final String category;
	private final String name;
	private final int[] itemIds;

	PageDefinition(String category, String name, int[] itemIds)
	{
		this.category = category;
		this.name = name;
		this.itemIds = Arrays.copyOf(itemIds, itemIds.length);
	}

	String getCategory()
	{
		return category;
	}

	String getName()
	{
		return name;
	}

	int[] getItemIds()
	{
		return Arrays.copyOf(itemIds, itemIds.length);
	}
}
