package com.collectionlogexporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class ExporterPanel extends PluginPanel
{
	private final Runnable syncAction;
	private final BiConsumer<Path, ExportOptions> exportAction;
	private final Supplier<EstimateMode> configuredEstimateMode;
	private final JLabel checklistMessage = new JLabel(
		"<html><b>Optional: open uncovered pages for KC.</b><br>"
		+ "The complete item list is available after Sync.</html>");
	private final JLabel status = new JLabel("<html>Open your Collection Log to sync.</html>");
	private final JLabel count = new JLabel("No snapshot this session");
	private final JLabel checklistProgress = new JLabel(
		"<html>0 / 0 pages<br>with KC coverage</html>");
	private final JLabel hiscoreStatus = new JLabel(
		"<html>Jagex hiscore KC lookup<br>starts after Sync</html>");
	private final JButton syncButton = new JButton("Sync open Collection Log");
	private final JButton exportButton = new JButton("Choose export...");
	private final JPanel checklistPanel = new JPanel();
	private final Map<String, List<JCheckBox>> pageChecks = new HashMap<>();
	private final List<PageDefinition> checklistDefinitions = new ArrayList<>();
	private final Set<String> coveredPageKeys = new HashSet<>();
	private boolean exportUnlocked;

	ExporterPanel(
		Runnable syncAction,
		BiConsumer<Path, ExportOptions> exportAction,
		Supplier<EstimateMode> configuredEstimateMode)
	{
		super();
		this.syncAction = syncAction;
		this.exportAction = exportAction;
		this.configuredEstimateMode = configuredEstimateMode;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("<html><b>Collection Log Exporter</b></html>");
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		content.add(title);
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		checklistMessage.setForeground(new Color(255, 193, 7));
		checklistMessage.setAlignmentX(LEFT_ALIGNMENT);
		content.add(checklistMessage);
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		JLabel hint = new JLabel(
			"<html>Checked pages have KC/attempt coverage. Unchecked pages still "
			+ "export, using a baseline estimate until inspected.</html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setAlignmentX(LEFT_ALIGNMENT);
		content.add(hint);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		status.setForeground(new Color(255, 193, 7));
		status.setAlignmentX(LEFT_ALIGNMENT);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setAlignmentX(LEFT_ALIGNMENT);
		checklistProgress.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		checklistProgress.setAlignmentX(LEFT_ALIGNMENT);
		hiscoreStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hiscoreStatus.setAlignmentX(LEFT_ALIGNMENT);
		content.add(status);
		content.add(Box.createRigidArea(new Dimension(0, 4)));
		content.add(count);
		content.add(Box.createRigidArea(new Dimension(0, 4)));
		content.add(checklistProgress);
		content.add(Box.createRigidArea(new Dimension(0, 4)));
		content.add(hiscoreStatus);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		syncButton.setAlignmentX(LEFT_ALIGNMENT);
		syncButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		syncButton.addActionListener(event -> syncAction.run());
		content.add(syncButton);
		content.add(Box.createRigidArea(new Dimension(0, 6)));

		exportButton.setAlignmentX(LEFT_ALIGNMENT);
		exportButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		exportButton.setEnabled(false);
		exportButton.addActionListener(event -> chooseExport());
		content.add(exportButton);
		content.add(Box.createRigidArea(new Dimension(0, 14)));

		checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
		checklistPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		checklistPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(checklistPanel);
		content.add(Box.createVerticalGlue());
		add(content, BorderLayout.CENTER);
	}

	void setPageChecklist(List<PageDefinition> definitions, Set<String> visitedPages)
	{
		List<PageDefinition> definitionCopy = new ArrayList<>(definitions);
		Set<String> visitedCopy = new HashSet<>(visitedPages);
		runOnEdt(() ->
		{
			checklistDefinitions.clear();
			checklistDefinitions.addAll(definitionCopy);
			coveredPageKeys.clear();
			coveredPageKeys.addAll(visitedCopy);
			rebuildChecklist();
		});
	}

	void markPageVisited(String pageName)
	{
		String key = normalizePageName(pageName);
		runOnEdt(() ->
		{
			if (coveredPageKeys.add(key))
			{
				rebuildChecklist();
			}
		});
	}

	void setSyncing()
	{
		runOnEdt(() ->
		{
			exportUnlocked = false;
			status.setText("Syncing Collection Log...");
			status.setForeground(new Color(255, 193, 7));
			syncButton.setEnabled(false);
			exportButton.setEnabled(false);
		});
	}

	void setReady(
		int obtainedItems,
		int officialObtained,
		int officialTotal,
		int incompletePages,
		int missingItems,
		int visitedPages,
		int totalPages)
	{
		runOnEdt(() ->
		{
			exportUnlocked = totalPages > 0;
			status.setText("Snapshot ready - export available");
			status.setForeground(new Color(76, 175, 80));
			String official = officialObtained >= 0 && officialTotal > 0
				? "Official log: " + officialObtained + " / " + officialTotal + "<br>"
				: "";
			int officialMissing = officialObtained >= 0 && officialTotal >= officialObtained
				? officialTotal - officialObtained
				: -1;
			String missing = officialMissing >= 0
				? officialMissing + " unique missing slots (official)"
				: missingItems + " unique missing slots";
			if (officialMissing >= 0 && missingItems != officialMissing)
			{
				missing += "<br>" + missingItems + " currently identified by item scan";
			}
			count.setText("<html>" + official
				+ obtainedItems + " owned item IDs captured<br>"
				+ incompletePages + " incomplete pages<br>" + missing + "</html>");
			checklistProgress.setText("<html>" + visitedPages + " / " + totalPages
				+ " pages<br>with KC coverage</html>");
			checklistMessage.setForeground(visitedPages >= totalPages && totalPages > 0
				? new Color(76, 175, 80)
				: new Color(255, 193, 7));
			syncButton.setEnabled(true);
			exportButton.setEnabled(exportUnlocked);
		});
	}

	void setNeedsLog(String message)
	{
		runOnEdt(() ->
		{
			exportUnlocked = false;
			status.setText("<html>" + message + "</html>");
			status.setForeground(new Color(255, 193, 7));
			syncButton.setEnabled(true);
			exportButton.setEnabled(false);
		});
	}

	void setHiscoreLookingUp()
	{
		runOnEdt(() ->
		{
			hiscoreStatus.setText(
				"<html>Looking up public Jagex<br>hiscores for KC...</html>");
			hiscoreStatus.setForeground(new Color(255, 193, 7));
		});
	}

	void setHiscoreCoverage(int hiscorePages, int coveredPages, int totalPages)
	{
		runOnEdt(() ->
		{
			hiscoreStatus.setText("<html>Jagex hiscores filled " + hiscorePages
				+ " pages.<br>" + Math.max(0, totalPages - coveredPages)
				+ " pages remain for optional manual inspection.</html>");
			hiscoreStatus.setForeground(new Color(76, 175, 80));
		});
	}

	void setHiscoreUnavailable(String message)
	{
		runOnEdt(() ->
		{
			hiscoreStatus.setText("<html>" + message + "</html>");
			hiscoreStatus.setForeground(new Color(255, 193, 7));
		});
	}

	void setExporting()
	{
		runOnEdt(() ->
		{
			status.setText("Writing export...");
			status.setForeground(new Color(255, 193, 7));
			exportButton.setEnabled(false);
		});
	}

	void setExportResult(String message, boolean success)
	{
		runOnEdt(() ->
		{
			status.setText("<html>" + message + "</html>");
			status.setForeground(success ? new Color(76, 175, 80) : new Color(244, 67, 54));
			exportButton.setEnabled(exportUnlocked);
		});
	}

	boolean isExportUnlocked()
	{
		return exportUnlocked;
	}

	boolean isPageChecked(String pageName)
	{
		List<JCheckBox> checks = pageChecks.get(normalizePageName(pageName));
		return checks != null && !checks.isEmpty()
			&& checks.stream().allMatch(JCheckBox::isSelected);
	}

	int pageListPosition(String pageName)
	{
		List<JCheckBox> checks = pageChecks.get(normalizePageName(pageName));
		return checks == null || checks.isEmpty()
			? -1
			: checklistPanel.getComponentZOrder(checks.get(0));
	}

	String countText()
	{
		return count.getText();
	}

	private void rebuildChecklist()
	{
		pageChecks.clear();
		checklistPanel.removeAll();
		addChecklistSection("Remaining KC coverage", false);
		checklistPanel.add(Box.createRigidArea(new Dimension(0, 14)));
		addChecklistSection("Covered KC pages", true);
		checklistPanel.revalidate();
		checklistPanel.repaint();
	}

	private void addChecklistSection(String title, boolean covered)
	{
		JLabel section = new JLabel("<html><b>" + title + "</b></html>");
		section.setForeground(covered ? new Color(76, 175, 80) : new Color(255, 193, 7));
		section.setAlignmentX(LEFT_ALIGNMENT);
		checklistPanel.add(section);
		checklistPanel.add(Box.createRigidArea(new Dimension(0, 6)));

		String previousCategory = null;
		int added = 0;
		for (PageDefinition definition : checklistDefinitions)
		{
			String key = normalizePageName(definition.getName());
			if (coveredPageKeys.contains(key) != covered)
			{
				continue;
			}

			if (!definition.getCategory().equals(previousCategory))
			{
				if (previousCategory != null)
				{
					checklistPanel.add(Box.createRigidArea(new Dimension(0, 7)));
				}
				JLabel category = new JLabel("<html><b>"
					+ definition.getCategory() + "</b></html>");
				category.setForeground(Color.WHITE);
				category.setAlignmentX(LEFT_ALIGNMENT);
				checklistPanel.add(category);
				previousCategory = definition.getCategory();
			}

			JCheckBox page = new JCheckBox(definition.getName(), covered);
			page.setEnabled(false);
			page.setOpaque(false);
			page.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			page.setAlignmentX(LEFT_ALIGNMENT);
			page.setToolTipText(definition.getName());
			checklistPanel.add(page);
			pageChecks.computeIfAbsent(key, ignored -> new ArrayList<>()).add(page);
			added++;
		}

		if (added == 0)
		{
			JLabel empty = new JLabel(covered ? "None yet" : "None - all pages covered");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setAlignmentX(LEFT_ALIGNMENT);
			checklistPanel.add(empty);
		}
	}

	private void chooseExport()
	{
		JComboBox<ExportFormat> format = new JComboBox<>(ExportFormat.values());
		JComboBox<DetailLevel> detail = new JComboBox<>(DetailLevel.values());
		JComboBox<SortMode> sort = new JComboBox<>(SortMode.values());
		JComboBox<EstimateMode> estimates = new JComboBox<>(EstimateMode.values());
		estimates.setSelectedItem(configuredEstimateMode.get());

		JPanel options = new JPanel(new GridLayout(0, 1, 0, 4));
		options.add(new JLabel("File format"));
		options.add(format);
		options.add(new JLabel("Detail"));
		options.add(detail);
		options.add(new JLabel("Sort pages by"));
		options.add(sort);
		options.add(new JLabel("Estimate profile"));
		options.add(estimates);

		Window owner = SwingUtilities.getWindowAncestor(this);
		int result = JOptionPane.showConfirmDialog(
			owner,
			options,
			"Export options",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
		{
			return;
		}

		ExportFormat selectedFormat = (ExportFormat) format.getSelectedItem();
		ExportOptions selectedOptions = new ExportOptions(
			selectedFormat,
			(DetailLevel) detail.getSelectedItem(),
			(SortMode) sort.getSelectedItem(),
			(EstimateMode) estimates.getSelectedItem());

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Collection Log export");
		chooser.setSelectedFile(new java.io.File("collection-log." + selectedFormat.getExtension()));
		chooser.setFileFilter(new FileNameExtensionFilter(
			selectedFormat.toString(),
			selectedFormat.getExtension()));
		if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		Path target = withExtension(chooser.getSelectedFile().toPath(), selectedFormat.getExtension());
		if (java.nio.file.Files.exists(target))
		{
			int overwrite = JOptionPane.showConfirmDialog(
				owner,
				"Replace " + target.getFileName() + "?",
				"Confirm overwrite",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if (overwrite != JOptionPane.YES_OPTION)
			{
				return;
			}
		}
		exportAction.accept(target, selectedOptions);
	}

	static Path withExtension(Path path, String extension)
	{
		String name = path.getFileName().toString();
		if (name.toLowerCase().endsWith("." + extension.toLowerCase()))
		{
			return path;
		}
		return path.resolveSibling(name + "." + extension);
	}

	private static void runOnEdt(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			SwingUtilities.invokeLater(runnable);
		}
	}

	private static String normalizePageName(String pageName)
	{
		return pageName == null ? "" : pageName.trim().toLowerCase(Locale.ENGLISH);
	}
}
