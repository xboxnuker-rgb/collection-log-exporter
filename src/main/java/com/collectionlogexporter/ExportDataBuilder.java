package com.collectionlogexporter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

final class ExportDataBuilder
{
	private final EstimateCatalog catalog;

	ExportDataBuilder(EstimateCatalog catalog)
	{
		this.catalog = catalog;
	}

	ExportData build(
		String playerName,
		List<PageDefinition> definitions,
		Map<Integer, Integer> quantities,
		IntFunction<String> itemNameLookup,
		Function<String, String> pageCounterLookup,
		ItemCounterLookup itemCounterLookup,
		boolean ironman,
		SortMode sortMode,
		int officialObtained,
		int officialTotal)
	{
		Set<String> obtainedNames = OwnedItems.names(quantities, itemNameLookup);

		Set<Integer> uniqueDefinitionItems = new HashSet<>();
		for (PageDefinition definition : definitions)
		{
			for (int itemId : definition.getItemIds())
			{
				uniqueDefinitionItems.add(itemId);
			}
		}
		int scannedObtained = 0;
		for (int itemId : uniqueDefinitionItems)
		{
			if (OwnedItems.contains(itemId, quantities, obtainedNames, itemNameLookup))
			{
				scannedObtained++;
			}
		}

		List<PageWork> work = new ArrayList<>();
		int logOrder = 0;
		for (PageDefinition definition : definitions)
		{
			String counterText = pageCounterLookup.apply(definition.getName());
			int completedAttempts = AttemptCounter.fromText(counterText);
			int obtained = 0;
			int itemsAtOrOverRate = 0;
			double maxKcOverRate = completedAttempts >= 0 ? 0.0 : Double.NaN;
			List<ItemWork> missing = new ArrayList<>();
			boolean allEstimatesKnown = true;
			boolean allAnytime = true;
			double pageHours = 0.0;
			Map<String, Double> sequentialPageHours = new HashMap<>();
			for (int itemId : definition.getItemIds())
			{
				if (OwnedItems.contains(itemId, quantities, obtainedNames, itemNameLookup))
				{
					obtained++;
					continue;
				}

				String itemName = itemNameLookup.apply(itemId);
				ItemEstimate estimate = ScrollCaseItems.applyMilestone(
					itemId,
					catalog.estimate(itemId, itemName, ironman));
				String itemCounterText = itemCounterLookup.apply(definition.getName(), itemId);
				int itemCompletedAttempts = AttemptCounter.fromText(itemCounterText);
				double effectiveHours = estimate.effectiveHours(itemCompletedAttempts);
				boolean anytime = estimate.isAnytime(itemCompletedAttempts);
				double kcOverRate = estimate.kcOverRate(itemCompletedAttempts);
				if (estimate.isKnown())
				{
					if (ScrollCaseItems.isScrollCase(itemId))
					{
						sequentialPageHours.merge(
							ScrollCaseItems.progressionGroup(itemId),
							effectiveHours,
							Math::max);
					}
					else
					{
						pageHours += effectiveHours;
					}
				}
				else
				{
					allEstimatesKnown = false;
				}
				if (anytime)
				{
					itemsAtOrOverRate++;
					maxKcOverRate = Double.isFinite(maxKcOverRate)
						? Math.max(maxKcOverRate, kcOverRate)
						: kcOverRate;
				}
				else
				{
					allAnytime = false;
				}
				missing.add(new ItemWork(
					itemId,
					itemName,
					estimate,
					itemCompletedAttempts,
					itemCounterText,
					effectiveHours,
					anytime));
			}
			for (double sequentialHours : sequentialPageHours.values())
			{
				pageHours += sequentialHours;
			}

			if (!missing.isEmpty())
			{
				double estimate = allEstimatesKnown ? pageHours : Double.NaN;
				work.add(new PageWork(
					definition,
					obtained,
					definition.getItemIds().length,
					missing,
					estimate,
					allEstimatesKnown && allAnytime,
					counterText,
					completedAttempts,
					itemsAtOrOverRate,
					maxKcOverRate,
					logOrder));
			}
			logOrder++;
		}

		List<PageWork> ranking = new ArrayList<>(work);
		ranking.sort(Comparator
			.comparingDouble((PageWork page) -> sortableHours(page.estimatedHours))
			.thenComparingInt(page -> page.missing.size())
			.thenComparing(page -> page.definition.getName()));
		Map<PageWork, Integer> ranks = new HashMap<>();
		for (int index = 0; index < ranking.size(); index++)
		{
			ranks.put(ranking.get(index), index + 1);
		}

		work.sort(pageComparator(sortMode));
		List<PageSummary> summaries = new ArrayList<>();
		List<ExportRow> rows = new ArrayList<>();
		Set<Integer> emittedMissingItems = new HashSet<>();
		for (PageWork page : work)
		{
			int rank = ranks.get(page);
			PageSummary summary = new PageSummary(
				page.definition.getCategory(),
				page.definition.getName(),
				page.obtained,
				page.total,
				page.missing.size(),
				page.estimatedHours,
				page.anytime,
				page.counterText,
				page.completedAttempts,
				page.itemsAtOrOverRate,
				page.maxKcOverRate);
			summary.setRank(rank);
			summaries.add(summary);

			page.missing.sort(Comparator
				.comparingDouble((ItemWork item) -> sortableHours(item.effectiveHours))
				.thenComparing(item -> item.itemName));
			for (ItemWork item : page.missing)
			{
				if (!emittedMissingItems.add(item.itemId))
				{
					continue;
				}
				rows.add(new ExportRow(
					page.definition.getCategory(),
					page.definition.getName(),
					page.obtained,
					page.total,
					page.missing.size(),
					item.itemId,
					item.itemName,
					item.estimate,
					item.completedAttempts,
					item.effectiveHours,
					item.anytime,
					page.estimatedHours,
					page.anytime,
					rank,
					item.counterText));
			}
		}

		return new ExportData(
			playerName == null ? "" : playerName,
			Instant.now(),
			ironman ? "Ironman" : "Main",
			officialObtained,
			officialTotal,
			scannedObtained,
			uniqueDefinitionItems.size(),
			summaries,
			rows);
	}

	private static Comparator<PageWork> pageComparator(SortMode sortMode)
	{
		switch (sortMode)
		{
			case FEWEST:
				return Comparator.comparingInt((PageWork page) -> page.missing.size())
					.thenComparingDouble(page -> sortableHours(page.estimatedHours))
					.thenComparingInt(page -> page.logOrder);
			case LOG_ORDER:
				return Comparator.comparingInt(page -> page.logOrder);
			case CLOSEST:
			default:
				return Comparator
					.comparingDouble((PageWork page) -> sortableHours(page.estimatedHours))
					.thenComparingInt(page -> page.missing.size())
					.thenComparingInt(page -> page.logOrder);
		}
	}

	private static double sortableHours(double hours)
	{
		return Double.isFinite(hours) ? hours : Double.POSITIVE_INFINITY;
	}

	private static final class PageWork
	{
		private final PageDefinition definition;
		private final int obtained;
		private final int total;
		private final List<ItemWork> missing;
		private final double estimatedHours;
		private final boolean anytime;
		private final String counterText;
		private final int completedAttempts;
		private final int itemsAtOrOverRate;
		private final double maxKcOverRate;
		private final int logOrder;

		private PageWork(
			PageDefinition definition,
			int obtained,
			int total,
			List<ItemWork> missing,
			double estimatedHours,
			boolean anytime,
			String counterText,
			int completedAttempts,
			int itemsAtOrOverRate,
			double maxKcOverRate,
			int logOrder)
		{
			this.definition = definition;
			this.obtained = obtained;
			this.total = total;
			this.missing = missing;
			this.estimatedHours = estimatedHours;
			this.anytime = anytime;
			this.counterText = counterText;
			this.completedAttempts = completedAttempts;
			this.itemsAtOrOverRate = itemsAtOrOverRate;
			this.maxKcOverRate = maxKcOverRate;
			this.logOrder = logOrder;
		}
	}

	private static final class ItemWork
	{
		private final int itemId;
		private final String itemName;
		private final ItemEstimate estimate;
		private final int completedAttempts;
		private final String counterText;
		private final double effectiveHours;
		private final boolean anytime;

		private ItemWork(
			int itemId,
			String itemName,
			ItemEstimate estimate,
			int completedAttempts,
			String counterText,
			double effectiveHours,
			boolean anytime)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.estimate = estimate;
			this.completedAttempts = completedAttempts;
			this.counterText = counterText;
			this.effectiveHours = effectiveHours;
			this.anytime = anytime;
		}
	}
}
