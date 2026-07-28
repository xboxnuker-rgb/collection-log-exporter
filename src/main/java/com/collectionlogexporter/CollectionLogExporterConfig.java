package com.collectionlogexporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CollectionLogExporterConfig.GROUP)
public interface CollectionLogExporterConfig extends Config
{
	String GROUP = "collection-log-exporter";

	@ConfigItem(
		keyName = "estimateMode",
		name = "Estimate rates",
		description = "Choose main or iron rates, or detect the current account type"
	)
	default EstimateMode estimateMode()
	{
		return EstimateMode.AUTO;
	}
}
