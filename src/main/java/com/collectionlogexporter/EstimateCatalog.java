package com.collectionlogexporter;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EstimateCatalog
{
	private static final String BASE = "/com/collectionlogexporter/data/";

	private final Map<Integer, List<Candidate>> candidatesByItemId;
	private final Map<String, List<Candidate>> candidatesByItemName;

	private EstimateCatalog(
		Map<Integer, List<Candidate>> candidatesByItemId,
		Map<String, List<Candidate>> candidatesByItemName)
	{
		this.candidatesByItemId = candidatesByItemId;
		this.candidatesByItemName = candidatesByItemName;
	}

	static EstimateCatalog load(Gson gson) throws IOException
	{
		RawActivity[] rawActivities = read(gson, "activities.json", RawActivity[].class);
		RawMapping[] rawMappings = read(gson, "activity_map.json", RawMapping[].class);

		Map<Integer, RawActivity> activities = new HashMap<>();
		for (RawActivity activity : rawActivities)
		{
			activities.put(activity.index, activity);
		}

		Map<Integer, List<Candidate>> byId = new HashMap<>();
		Map<String, List<Candidate>> byName = new HashMap<>();
		for (RawMapping mapping : rawMappings)
		{
			RawActivity activity = activities.get(mapping.activityIndex);
			if (activity == null || mapping.dropRateAttempts <= 0.0)
			{
				continue;
			}
			Candidate candidate = new Candidate(activity, mapping);
			byId.computeIfAbsent(mapping.itemId, ignored -> new ArrayList<>()).add(candidate);
			byName.computeIfAbsent(normalize(mapping.itemName), ignored -> new ArrayList<>()).add(candidate);
		}

		makeImmutable(byId);
		makeImmutable(byName);
		return new EstimateCatalog(
			Collections.unmodifiableMap(byId),
			Collections.unmodifiableMap(byName));
	}

	ItemEstimate estimate(int itemId, String itemName, boolean ironman)
	{
		List<Candidate> candidates = candidatesByItemId.get(itemId);
		if ((candidates == null || candidates.isEmpty()) && itemName != null)
		{
			candidates = candidatesByItemName.get(normalize(itemName));
		}
		if (candidates == null || candidates.isEmpty())
		{
			return ItemEstimate.UNKNOWN;
		}

		ItemEstimate best = ItemEstimate.UNKNOWN;
		for (Candidate candidate : candidates)
		{
			double attemptsPerHour = ironman
				? candidate.activity.completionsPerHrIron
				: candidate.activity.completionsPerHrMain;
			if (attemptsPerHour <= 0.0)
			{
				continue;
			}
			double hours = candidate.mapping.dropRateAttempts / attemptsPerHour
				+ candidate.activity.extraTimeFirst;
			if (!best.isKnown() || hours < best.getEstimatedHours())
			{
				best = new ItemEstimate(
					candidate.activity.name,
					attemptsPerHour,
					candidate.mapping.dropRateAttempts,
					hours,
					method(candidate.mapping));
			}
		}
		return best;
	}

	int supportedItemCount()
	{
		return candidatesByItemId.size();
	}

	private static String method(RawMapping mapping)
	{
		if (mapping.exact && mapping.independent)
		{
			return "Exact + independent";
		}
		if (mapping.exact)
		{
			return "Exact";
		}
		if (mapping.independent)
		{
			return "Independent";
		}
		return mapping.requiresPrevious ? "Sequential" : "Standard";
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}

	private static <K> void makeImmutable(Map<K, List<Candidate>> map)
	{
		for (Map.Entry<K, List<Candidate>> entry : map.entrySet())
		{
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
	}

	private static <T> T read(Gson gson, String name, Class<T> type) throws IOException
	{
		try (InputStream input = EstimateCatalog.class.getResourceAsStream(BASE + name))
		{
			if (input == null)
			{
				throw new IOException("Missing estimate resource: " + name);
			}
			try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, type);
			}
		}
	}

	private static final class Candidate
	{
		private final RawActivity activity;
		private final RawMapping mapping;

		private Candidate(RawActivity activity, RawMapping mapping)
		{
			this.activity = activity;
			this.mapping = mapping;
		}
	}

	private static final class RawActivity
	{
		private int index;
		private String name;
		private double completionsPerHrMain;
		private double completionsPerHrIron;
		private double extraTimeFirst;
	}

	private static final class RawMapping
	{
		private int activityIndex;
		private int itemId;
		private String itemName;
		private boolean requiresPrevious;
		private boolean exact;
		private boolean independent;
		private double dropRateAttempts;
	}
}
