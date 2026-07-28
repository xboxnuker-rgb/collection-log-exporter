package com.collectionlogexporter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.StructComposition;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Collection Log Exporter",
	description = "Export remaining Collection Log slots and loose completion estimates",
	tags = {"collection", "log", "clog", "export", "excel", "xlsx", "ods", "csv", "completion"}
)
public class CollectionLogExporterPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogExporterPlugin.class);

	private static final int COLLECTION_DELAYED_TRANSMIT = 4100;
	private static final int COLLECTION_INIT = 2240;
	private static final int COLLECTION_LOG_SETUP = 7797;
	private static final int COLLECTION_TAB_ENUM = 2102;
	private static final int TAB_PAGE_ENUM_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEM_ENUM_PARAM = 690;
	private static final int EMPTY_SNAPSHOT_WAIT_TICKS = 8;
	private static final int TRANSMIT_SETTLE_TICKS = 3;
	private static final String QUANTITIES_KEY = "obtainedQuantities";
	private static final String PAGE_COUNTERS_KEY = "pageCounters";
	private static final Pattern OFFICIAL_PROGRESS = Pattern.compile(
		"Collection\\s+Log\\s*[-\\u2013\\u2014]\\s*([\\d,]+)\\s*/\\s*([\\d,]+)",
		Pattern.CASE_INSENSITIVE);

	private static final String[] CATEGORY_NAMES = {
		"Bosses",
		"Raids",
		"Clues",
		"Minigames",
		"Other"
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CollectionLogExporterConfig config;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	@Inject
	private HiscoreClient hiscoreClient;

	private final List<PageDefinition> definitions = new ArrayList<>();
	private final Map<Integer, Integer> obtainedQuantities = new HashMap<>();
	private final Map<Integer, Integer> harvest = new HashMap<>();
	private final Map<String, String> pageCounters = new HashMap<>();
	private final Map<String, String> hiscoreCounters = new HashMap<>();
	private final Set<String> visitedPages = new HashSet<>();

	private EstimateCatalog estimateCatalog;
	private ExportDataBuilder exportDataBuilder;
	private ExporterPanel panel;
	private NavigationButton navigationButton;

	private boolean syncing;
	private boolean snapshotReady;
	private boolean passiveHarvestDirty;
	private int snapshotStartTick = -1;
	private int lastTransmitTick = -1;
	private int passiveLastTransmitTick = -1;
	private int officialObtained = -1;
	private int officialTotal = -1;
	private int hiscoreLookupVersion;

	@Provides
	CollectionLogExporterConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(CollectionLogExporterConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		estimateCatalog = EstimateCatalog.load(gson);
		exportDataBuilder = new ExportDataBuilder(estimateCatalog);
		loadPersistedQuantities();
		loadPersistedPageCounters();

		panel = new ExporterPanel(this::requestSyncFromPanel, this::requestExport, config::estimateMode);
		navigationButton = NavigationButton.builder()
			.tooltip("Collection Log Exporter")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		log.info("Collection Log Exporter started with estimates for {} item ids",
			estimateCatalog.supportedItemCount());
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::tryInitialSync);
		}
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		definitions.clear();
		harvest.clear();
		visitedPages.clear();
		hiscoreCounters.clear();
		resetLiveSnapshot();
		panel = null;
		navigationButton = null;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION && !syncing && !snapshotReady)
		{
			scheduleInterfaceSync();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST && !isAnotherPlayersLog())
		{
			clientThread.invokeLater(() ->
			{
				captureVisiblePageCounter();
				return true;
			});
		}
		if (event.getScriptId() == COLLECTION_LOG_SETUP && !syncing && !snapshotReady)
		{
			scheduleInterfaceSync();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_DELAYED_TRANSMIT)
		{
			return;
		}
		if (isAnotherPlayersLog())
		{
			if (syncing)
			{
				cancelSync("Open your own Collection Log to sync.");
			}
			return;
		}

		ScriptEvent scriptEvent = event.getScriptEvent();
		Object[] arguments = scriptEvent == null ? null : scriptEvent.getArguments();
		if (arguments == null || arguments.length < 3
			|| !(arguments[1] instanceof Integer) || !(arguments[2] instanceof Integer))
		{
			return;
		}

		int itemId = (Integer) arguments[1];
		int quantity = (Integer) arguments[2];
		if (itemId > 0 && quantity > 0)
		{
			if (syncing)
			{
				harvest.put(itemId, quantity);
			}
			else if (!Integer.valueOf(quantity).equals(obtainedQuantities.put(itemId, quantity)))
			{
				// RuneProfile, WikiSync, and the native Search control use this
				// same whole-log transmission. Passively merge those snapshots so
				// one partial request can never erase a previously confirmed slot.
				passiveHarvestDirty = true;
				passiveLastTransmitTick = client.getTickCount();
			}
		}
		if (syncing)
		{
			lastTransmitTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!syncing)
		{
			if (passiveHarvestDirty
				&& passiveLastTransmitTick + TRANSMIT_SETTLE_TICKS < client.getTickCount())
			{
				passiveHarvestDirty = false;
				passiveLastTransmitTick = -1;
				persistQuantities();
				updateReadyStatus();
			}
			return;
		}

		int tick = client.getTickCount();
		boolean transmissionsSettled = lastTransmitTick >= 0
			&& lastTransmitTick + TRANSMIT_SETTLE_TICKS < tick;
		boolean emptySnapshotSettled = lastTransmitTick < 0
			&& snapshotStartTick + EMPTY_SNAPSHOT_WAIT_TICKS < tick;
		if (transmissionsSettled || emptySnapshotSettled)
		{
			finishSync();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				loadPersistedQuantities();
				loadPersistedPageCounters();
				return true;
			});
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			definitions.clear();
			harvest.clear();
			visitedPages.clear();
			resetLiveSnapshot();
			if (panel != null)
			{
				panel.setPageChecklist(Collections.emptyList(), Collections.emptySet());
				panel.setNeedsLog("Log in and open your Collection Log to sync.");
			}
		}
	}

	private boolean tryInitialSync()
	{
		if (client.getWidget(InterfaceID.Collection.FRAME) != null)
		{
			scheduleInterfaceSync();
		}
		return true;
	}

	private void requestSyncFromPanel()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				panel.setNeedsLog("Log in and open your Collection Log to sync.");
				return true;
			}
			if (client.getWidget(InterfaceID.Collection.FRAME) == null)
			{
				panel.setNeedsLog("Open your Collection Log in-game, then press Sync.");
				return true;
			}
			beginSync();
			return true;
		});
	}

	private void scheduleInterfaceSync()
	{
		// Dynamic page-title children are populated after the setup event.
		clientThread.invokeLater(() ->
		{
			clientThread.invokeLater(() ->
			{
				if (!syncing && !snapshotReady
					&& client.getWidget(InterfaceID.Collection.FRAME) != null)
				{
					beginSync();
				}
				return true;
			});
			return true;
		});
	}

	private void beginSync()
	{
		if (syncing)
		{
			return;
		}
		if (isAnotherPlayersLog())
		{
			panel.setNeedsLog("That is another player's log. Open your own Collection Log.");
			return;
		}
		if (!loadPageDefinitions())
		{
			panel.setNeedsLog("Collection Log pages are still loading. Try Sync again.");
			return;
		}
		visitedPages.clear();
		hiscoreCounters.clear();
		hiscoreLookupVersion++;
		officialObtained = -1;
		officialTotal = -1;
		panel.setPageChecklist(counterDefinitions(), counterCoveredPages());
		captureOfficialProgress();

		Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
		if (searchButton == null)
		{
			panel.setNeedsLog("Collection Log Search is unavailable. Reopen the log and retry.");
			return;
		}

		harvest.clear();
		syncing = true;
		snapshotReady = false;
		snapshotStartTick = client.getTickCount();
		lastTransmitTick = -1;
		panel.setSyncing();

		client.menuAction(
			-1,
			InterfaceID.Collection.SEARCH_TOGGLE,
			MenuAction.CC_OP,
			1,
			-1,
			"Search",
			null);
		client.runScript(COLLECTION_INIT);
	}

	private boolean loadPageDefinitions()
	{
		EnumComposition tabEnum = client.getEnum(COLLECTION_TAB_ENUM);
		int[] tabStructIds = tabEnum == null ? null : tabEnum.getIntVals();
		if (tabStructIds == null || tabStructIds.length == 0)
		{
			return false;
		}

		List<PageDefinition> loaded = new ArrayList<>();
		int tabCount = Math.min(tabStructIds.length, CATEGORY_NAMES.length);
		for (int tabIndex = 0; tabIndex < tabCount; tabIndex++)
		{
			StructComposition tabStruct = client.getStructComposition(tabStructIds[tabIndex]);
			EnumComposition pageEnum = client.getEnum(tabStruct.getIntValue(TAB_PAGE_ENUM_PARAM));
			int[] pageStructIds = pageEnum == null ? null : pageEnum.getIntVals();
			if (pageStructIds == null)
			{
				continue;
			}

			for (int pageIndex = 0; pageIndex < pageStructIds.length; pageIndex++)
			{
				StructComposition pageStruct = client.getStructComposition(pageStructIds[pageIndex]);
				EnumComposition itemEnum = client.getEnum(pageStruct.getIntValue(PAGE_ITEM_ENUM_PARAM));
				int[] itemIds = itemEnum == null ? null : itemEnum.getIntVals();
				if (itemIds == null || itemIds.length == 0)
				{
					continue;
				}
				String rawPageName = pageStruct.getStringValue(PAGE_NAME_PARAM);
				if (rawPageName == null)
				{
					return false;
				}
				String pageName = Text.removeTags(rawPageName).trim();
				if (pageName.isEmpty())
				{
					return false;
				}
				loaded.add(new PageDefinition(CATEGORY_NAMES[tabIndex], pageName, itemIds));
			}
		}

		if (loaded.isEmpty())
		{
			return false;
		}
		definitions.clear();
		definitions.addAll(loaded);
		log.debug("Loaded {} Collection Log page definitions", definitions.size());
		return true;
	}

	private void finishSync()
	{
		syncing = false;
		obtainedQuantities.putAll(harvest);
		harvest.clear();
		loadPageDefinitions();
		captureVisiblePageCounter();
		captureOfficialProgress();
		snapshotReady = !definitions.isEmpty();
		panel.setPageChecklist(counterDefinitions(), counterCoveredPages());
		persistQuantities();
		updateReadyStatus();
		requestHiscoreLookup();
		log.debug("Collection Log snapshot ready with {} obtained item ids", obtainedQuantities.size());
	}

	private void requestHiscoreLookup()
	{
		if (client.getLocalPlayer() == null)
		{
			panel.setHiscoreUnavailable("Jagex hiscore lookup unavailable: no logged-in player name.");
			return;
		}

		String playerName = Text.removeTags(client.getLocalPlayer().getName());
		if (playerName == null || playerName.trim().isEmpty())
		{
			panel.setHiscoreUnavailable("Jagex hiscore lookup unavailable: no logged-in player name.");
			return;
		}

		int version = ++hiscoreLookupVersion;
		HiscoreEndpoint endpoint = localHiscoreEndpoint();
		panel.setHiscoreLookingUp();
		hiscoreClient.lookupAsync(playerName, endpoint).whenComplete((result, error) ->
			clientThread.invokeLater(() ->
			{
				if (version != hiscoreLookupVersion || panel == null || !snapshotReady)
				{
					return true;
				}
				if (error != null || result == null)
				{
					log.debug("Jagex hiscore lookup failed for {}", playerName, error);
					panel.setHiscoreUnavailable(
						"Jagex hiscore KC lookup was unavailable; cached and manually viewed counters still apply.");
					return true;
				}

				hiscoreCounters.clear();
				hiscoreCounters.putAll(HiscorePageCounters.map(
					(HiscoreResult) result,
					new ArrayList<>(definitions)));
				Set<String> covered = counterCoveredPages();
				List<PageDefinition> counterDefinitions = counterDefinitions();
				panel.setPageChecklist(counterDefinitions, covered);
				updateReadyStatus();
				panel.setHiscoreCoverage(
					hiscoreCounters.size(),
					covered.size(),
					counterDefinitions.size());
				log.debug("Jagex hiscores supplied counters for {} Collection Log pages",
					hiscoreCounters.size());
				return true;
			}));
	}

	private HiscoreEndpoint localHiscoreEndpoint()
	{
		HiscoreEndpoint worldEndpoint = HiscoreEndpoint.fromWorldTypes(client.getWorldType());
		if (worldEndpoint != HiscoreEndpoint.NORMAL)
		{
			return worldEndpoint;
		}
		switch (client.getVarbitValue(VarbitID.IRONMAN))
		{
			case 1:
				return HiscoreEndpoint.IRONMAN;
			case 2:
				return HiscoreEndpoint.ULTIMATE_IRONMAN;
			case 3:
				return HiscoreEndpoint.HARDCORE_IRONMAN;
			default:
				return HiscoreEndpoint.NORMAL;
		}
	}

	private void cancelSync(String message)
	{
		syncing = false;
		harvest.clear();
		snapshotStartTick = -1;
		lastTransmitTick = -1;
		if (panel != null)
		{
			panel.setNeedsLog(message);
		}
	}

	private void resetLiveSnapshot()
	{
		syncing = false;
		snapshotReady = false;
		passiveHarvestDirty = false;
		hiscoreCounters.clear();
		hiscoreLookupVersion++;
		officialObtained = -1;
		officialTotal = -1;
		snapshotStartTick = -1;
		lastTransmitTick = -1;
		passiveLastTransmitTick = -1;
	}

	private boolean isAnotherPlayersLog()
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
	}

	private void updateReadyStatus()
	{
		if (panel == null || !snapshotReady)
		{
			return;
		}
		int incompletePages = 0;
		Set<Integer> uniqueMissingItems = new HashSet<>();
		Set<String> obtainedNames = OwnedItems.names(obtainedQuantities, this::itemName);
		for (PageDefinition definition : definitions)
		{
			int missing = 0;
			for (int itemId : definition.getItemIds())
			{
				if (!OwnedItems.contains(
					itemId,
					obtainedQuantities,
					obtainedNames,
					this::itemName))
				{
					missing++;
					uniqueMissingItems.add(itemId);
				}
			}
			if (missing > 0)
			{
				incompletePages++;
			}
		}
		panel.setReady(
			obtainedQuantities.size(),
			officialObtained,
			officialTotal,
			incompletePages,
			uniqueMissingItems.size(),
			counterCoveredPages().size(),
			counterDefinitions().size());
	}

	private void requestExport(Path target, ExportOptions options)
	{
		panel.setExporting();
		clientThread.invokeLater(() ->
		{
			if (!snapshotReady || definitions.isEmpty())
			{
				panel.setNeedsLog("Open your Collection Log and sync before exporting.");
				return true;
			}
			boolean ironman = resolveIronman(options.getEstimateMode());
			String playerName = client.getLocalPlayer() == null
				? ""
				: Text.removeTags(client.getLocalPlayer().getName());
			ExportData data = exportDataBuilder.build(
				playerName,
				new ArrayList<>(definitions),
				new HashMap<>(obtainedQuantities),
				this::itemName,
				this::pageCounter,
				this::itemCounter,
				ironman,
				options.getSortMode(),
				officialObtained,
				officialTotal);

			scheduledExecutorService.execute(() -> writeExport(target, data, options));
			return true;
		});
	}

	private void writeExport(Path target, ExportData data, ExportOptions options)
	{
		try
		{
			SpreadsheetExporter.write(target, data, options);
			log.debug("Wrote Collection Log export to {}", target);
			if (panel != null)
			{
				panel.setExportResult("Saved " + Text.escapeJagex(target.getFileName().toString()), true);
			}
		}
		catch (IOException | RuntimeException exception)
		{
			log.warn("Unable to write Collection Log export", exception);
			if (panel != null)
			{
				String message = exception.getMessage() == null
					? exception.getClass().getSimpleName()
					: exception.getMessage();
				panel.setExportResult("Export failed: " + Text.escapeJagex(message), false);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private boolean resolveIronman(EstimateMode mode)
	{
		if (mode == EstimateMode.IRONMAN)
		{
			return true;
		}
		if (mode == EstimateMode.MAIN)
		{
			return false;
		}
		// 0 is a normal account; every other currently defined account type uses
		// the more conservative ironman estimate profile.
		return client.getVarbitValue(Varbits.ACCOUNT_TYPE) != 0;
	}

	private String itemName(int itemId)
	{
		String name = itemManager.getItemComposition(itemId).getName();
		return name == null || name.trim().isEmpty() ? "Item " + itemId : Text.removeTags(name);
	}

	private void captureVisiblePageCounter()
	{
		captureOfficialProgress();
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null)
		{
			return;
		}
		Widget[] children = header.getDynamicChildren();
		if (children == null || children.length < 1)
		{
			children = header.getChildren();
		}
		if (children == null || children.length < 1 || children[0] == null)
		{
			return;
		}

		String pageName = Text.removeTags(children[0].getText()).trim();
		if (pageName.isEmpty())
		{
			return;
		}
		List<String> counters = new ArrayList<>();
		for (int index = 2; index < children.length; index++)
		{
			Widget child = children[index];
			if (child == null)
			{
				continue;
			}
			String text = Text.removeTags(child.getText()).trim();
			if (!text.isEmpty() && text.matches(".*\\d.*"))
			{
				counters.add(text);
			}
		}
		if (counters.isEmpty())
		{
			String key = normalizePageName(pageName);
			if (pageCounters.remove(key) != null)
			{
				persistPageCounters();
			}
			return;
		}

		String counter = String.join("; ", counters);
		String key = normalizePageName(pageName);
		if (!counter.equals(pageCounters.put(key, counter)))
		{
			persistPageCounters();
			log.debug("Captured Collection Log counter for {}: {}", pageName, counter);
		}
		markPageVisited(pageName);
	}

	private String pageCounter(String pageName)
	{
		String captured = pageCounters.get(normalizePageName(pageName));
		if (captured != null && !captured.isEmpty())
		{
			return captured;
		}

		String hiscore = hiscoreCounters.get(normalizePageName(pageName));
		if (hiscore != null && !hiscore.isEmpty())
		{
			return hiscore;
		}

		Integer localKillCount = localKillCount(pageName);
		return localKillCount != null && localKillCount >= 0
			? "KC: " + localKillCount
			: "";
	}

	private String itemCounter(String pageName, int itemId)
	{
		String scrollCaseCounterPage = ScrollCaseItems.counterPageName(itemId);
		return scrollCaseCounterPage == null
			? pageCounter(pageName)
			: pageCounter(scrollCaseCounterPage);
	}

	private Integer localKillCount(String pageName)
	{
		Integer count = configManager.getRSProfileConfiguration(
			"killcount",
			pageName.toLowerCase(Locale.ENGLISH),
			int.class);
		if (count != null)
		{
			return count;
		}
		if ("Barrows".equalsIgnoreCase(pageName))
		{
			return configManager.getRSProfileConfiguration(
				"killcount",
				"barrows chests",
				int.class);
		}
		return null;
	}

	private void loadPersistedQuantities()
	{
		obtainedQuantities.clear();
		String value = configManager.getRSProfileConfiguration(
			CollectionLogExporterConfig.GROUP,
			QUANTITIES_KEY);
		if (value == null || value.trim().isEmpty())
		{
			return;
		}
		for (String entry : value.split(","))
		{
			String[] parts = entry.split(":", 2);
			if (parts.length != 2)
			{
				continue;
			}
			try
			{
				int itemId = Integer.parseInt(parts[0]);
				int quantity = Integer.parseInt(parts[1]);
				if (itemId > 0 && quantity > 0)
				{
					obtainedQuantities.put(itemId, quantity);
				}
			}
			catch (NumberFormatException ignored)
			{
				log.debug("Ignoring malformed saved Collection Log entry: {}", entry);
			}
		}
	}

	private void loadPersistedPageCounters()
	{
		pageCounters.clear();
		String value = configManager.getRSProfileConfiguration(
			CollectionLogExporterConfig.GROUP,
			PAGE_COUNTERS_KEY);
		if (value == null || value.trim().isEmpty())
		{
			return;
		}
		try
		{
			JsonObject object = gson.fromJson(value, JsonObject.class);
			if (object == null)
			{
				return;
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet())
			{
				if (entry.getValue() != null && entry.getValue().isJsonPrimitive())
				{
					pageCounters.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		}
		catch (RuntimeException exception)
		{
			log.debug("Ignoring malformed saved Collection Log page counters", exception);
		}
	}

	private void persistQuantities()
	{
		List<Integer> itemIds = new ArrayList<>(obtainedQuantities.keySet());
		Collections.sort(itemIds);
		StringBuilder value = new StringBuilder(itemIds.size() * 9);
		for (int itemId : itemIds)
		{
			if (value.length() > 0)
			{
				value.append(',');
			}
			value.append(itemId).append(':').append(obtainedQuantities.get(itemId));
		}
		configManager.setRSProfileConfiguration(
			CollectionLogExporterConfig.GROUP,
			QUANTITIES_KEY,
			value.toString());
	}

	private void persistPageCounters()
	{
		configManager.setRSProfileConfiguration(
			CollectionLogExporterConfig.GROUP,
			PAGE_COUNTERS_KEY,
			gson.toJson(pageCounters));
	}

	private static String normalizePageName(String pageName)
	{
		return pageName == null ? "" : pageName.trim().toLowerCase(Locale.ENGLISH);
	}

	private void captureOfficialProgress()
	{
		Widget frame = client.getWidget(InterfaceID.Collection.FRAME);
		int[] progress = findOfficialProgress(
			frame,
			Collections.newSetFromMap(new IdentityHashMap<>()));
		if (progress != null)
		{
			officialObtained = progress[0];
			officialTotal = progress[1];
		}
	}

	private int[] findOfficialProgress(Widget widget, Set<Widget> visited)
	{
		if (widget == null || !visited.add(widget))
		{
			return null;
		}
		String rawText = widget.getText();
		String text = rawText == null ? null : Text.removeTags(rawText);
		if (text != null)
		{
			Matcher matcher = OFFICIAL_PROGRESS.matcher(text);
			if (matcher.find())
			{
				try
				{
					return new int[]{
						Integer.parseInt(matcher.group(1).replace(",", "")),
						Integer.parseInt(matcher.group(2).replace(",", ""))
					};
				}
				catch (NumberFormatException ignored)
				{
					log.debug("Unable to parse Collection Log total from {}", text);
				}
			}
		}
		int[] result = findOfficialProgress(widget.getDynamicChildren(), visited);
		if (result != null)
		{
			return result;
		}
		result = findOfficialProgress(widget.getStaticChildren(), visited);
		if (result != null)
		{
			return result;
		}
		return findOfficialProgress(widget.getNestedChildren(), visited);
	}

	private int[] findOfficialProgress(Widget[] widgets, Set<Widget> visited)
	{
		if (widgets == null)
		{
			return null;
		}
		for (Widget widget : widgets)
		{
			int[] result = findOfficialProgress(widget, visited);
			if (result != null)
			{
				return result;
			}
		}
		return null;
	}

	private void markPageVisited(String pageName)
	{
		String key = normalizePageName(pageName);
		boolean knownPage = counterDefinitions().stream()
			.anyMatch(definition -> normalizePageName(definition.getName()).equals(key));
		if (knownPage && visitedPages.add(key))
		{
			if (panel != null)
			{
				panel.markPageVisited(pageName);
			}
			if (snapshotReady)
			{
				updateReadyStatus();
			}
		}
	}

	private Set<String> counterCoveredPages()
	{
		Set<String> covered = new HashSet<>();
		for (PageDefinition definition : counterDefinitions())
		{
			String key = normalizePageName(definition.getName());
			if (visitedPages.contains(key)
				|| !pageCounter(definition.getName()).isEmpty()
				|| hasItemCounterCoverage(definition))
			{
				covered.add(key);
			}
		}
		return covered;
	}

	private boolean hasItemCounterCoverage(PageDefinition definition)
	{
		boolean foundItemCounter = false;
		for (int itemId : definition.getItemIds())
		{
			if (!ScrollCaseItems.isScrollCase(itemId))
			{
				continue;
			}
			foundItemCounter = true;
			if (itemCounter(definition.getName(), itemId).isEmpty())
			{
				return false;
			}
		}
		return foundItemCounter;
	}

	private List<PageDefinition> counterDefinitions()
	{
		List<PageDefinition> counterPages = new ArrayList<>();
		for (PageDefinition definition : definitions)
		{
			String key = normalizePageName(definition.getName());
			boolean observedCounter = pageCounters.containsKey(key)
				|| hiscoreCounters.containsKey(key)
				|| hasItemCounterCoverage(definition)
				|| localKillCount(definition.getName()) != null;
			if (CounterPagePolicy.canHaveCounter(definition) || observedCounter)
			{
				counterPages.add(definition);
			}
		}
		return counterPages;
	}

	private static BufferedImage createIcon()
	{
		return ImageUtil.loadImageResource(
			CollectionLogExporterPlugin.class,
			"/com/collectionlogexporter/toolbar_icon.png");
	}
}
