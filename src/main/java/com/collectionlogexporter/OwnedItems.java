package com.collectionlogexporter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

final class OwnedItems
{
	private OwnedItems()
	{
	}

	static Set<String> names(
		Map<Integer, Integer> quantities,
		IntFunction<String> itemNameLookup)
	{
		Set<String> names = new HashSet<>();
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
		{
			if (entry.getValue() != null && entry.getValue() > 0)
			{
				names.add(normalizeName(itemNameLookup.apply(entry.getKey())));
			}
		}
		return names;
	}

	static boolean contains(
		int itemId,
		Map<Integer, Integer> quantities,
		Set<String> obtainedNames,
		IntFunction<String> itemNameLookup)
	{
		if (quantities.getOrDefault(itemId, 0) > 0)
		{
			return true;
		}
		String name = normalizeName(itemNameLookup.apply(itemId));
		return !name.isEmpty() && !name.startsWith("item ") && obtainedNames.contains(name);
	}

	private static String normalizeName(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}
}
